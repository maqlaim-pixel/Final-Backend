package com.travelvista.controller;

import com.travelvista.dto.LoginRequest;
import com.travelvista.model.PendingRegistration;
import com.travelvista.model.User;
import com.travelvista.repository.PendingRegistrationRepository;
import com.travelvista.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Transactional(noRollbackFor = AuthFailure.class)
public class CustomerAuthController {
    private static final Logger log = LoggerFactory.getLogger(CustomerAuthController.class);
    private final UserService userService;
    private final OtpService otpService;
    private final PendingRegistrationRepository pendingRegistrations;
    public CustomerAuthController(UserService userService, OtpService otpService,
                                  PendingRegistrationRepository pendingRegistrations) {
        this.userService = userService;
        this.otpService = otpService;
        this.pendingRegistrations = pendingRegistrations;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String name = body.get("name") == null ? "" : body.get("name").trim();
        String email = OtpService.normalizeEmail(body.get("email"));
        String password = body.get("password");
        if (name.isBlank() || name.length() > 150) return bad("Name is required (maximum 150 characters)");
        if (email == null || email.length() > 150 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}")) return bad("Please enter a valid email address");
        if (password == null || password.length() < 6 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            return bad("Password must be at least 6 characters and at most 72 bytes");
        String phone = UserService.normalizePhone(body.get("phone"));

        // Existing permanent accounts remain authoritative. Legacy inactive rows are
        // not modified by this new pending-registration flow.
        User existing = userService.lockByEmail(email);
        if (existing != null) return bad("Email is already registered");
        if (userService.findByPhone(phone) != null) return bad("Phone number is already registered");

        PendingRegistration pending = pendingRegistrations.findByEmail(email).orElse(null);
        if (pending == null) {
            pending = new PendingRegistration();
            pending.setEmail(email);
        }
        pending.setName(name);
        pending.setPhone(phone);
        pending.setPasswordHash(userService.encodePassword(password));
        pending.setCreatedAt(java.time.LocalDateTime.now());
        pending.setExpiresAt(java.time.LocalDateTime.now().plusMinutes(10));
        // This identifier binds the temporary record to the OTP challenge.
        pending.setTransactionId(java.util.UUID.randomUUID().toString());
        pendingRegistrations.save(pending);

        try {
            String transactionId = otpService.sendEmailOtp(email, OtpService.PURPOSE_REGISTER_EMAIL, name);
            pending.setTransactionId(transactionId);
            pending.setExpiresAt(java.time.LocalDateTime.now().plusMinutes(10));
            pendingRegistrations.save(pending);
            return challenge(email, transactionId, "REGISTRATION");
        } catch (RuntimeException failure) {
            // A provider failure must never leave a permanent user or a misleading
            // successful registration. Temporary data is safe to retry.
            pendingRegistrations.delete(pending);
            throw failure;
        }
    }

    @PostMapping("/register/verify")
    public ResponseEntity<?> verifyRegistration(@RequestBody Map<String, String> body) {
        String email = OtpService.normalizeEmail(body.get("email"));
        String transactionId = body.get("transactionId");
        PendingRegistration pending = transactionId == null ? null
                : pendingRegistrations.findByEmailAndTransactionId(email, transactionId).orElse(null);
        if (pending == null || pending.isExpired()) throw new AuthFailure(400, "Registration session expired. Please register again");

        verify(body, OtpService.PURPOSE_REGISTER_EMAIL);
        if (userService.lockByEmail(email) != null) throw new AuthFailure(400, "Email is already registered");
        if (userService.findByPhone(pending.getPhone()) != null) throw new AuthFailure(400, "Phone number is already registered");

        User user = new User(pending.getName(), pending.getEmail(), pending.getPhone(), pending.getPasswordHash(), null);
        // Only this successful OTP path creates and activates the permanent account.
        userService.enforceCustomerRole(user);
        user.setEmailVerified(true);
        user.setIsActive(true);
        user.setPhoneVerified(false);
        user.setUpdatedAt(java.time.LocalDateTime.now());
        userService.save(user);
        pendingRegistrations.delete(pending);
        return ResponseEntity.ok(userService.toLoginResponse(user));
    }

    @PostMapping("/register/resend")
    public ResponseEntity<?> resendRegistration(@RequestBody Map<String, String> body) {
        String email = OtpService.normalizeEmail(body.get("email"));
        PendingRegistration pending = pendingRegistrations.findByEmail(email).orElse(null);
        if (pending == null || pending.isExpired()) throw new AuthFailure(400, "No pending registration found for this email");
        try {
            String transactionId = otpService.sendEmailOtp(email, OtpService.PURPOSE_REGISTER_EMAIL, pending.getName());
            pending.setTransactionId(transactionId);
            pending.setCreatedAt(java.time.LocalDateTime.now());
            pending.setExpiresAt(java.time.LocalDateTime.now().plusMinutes(10));
            pendingRegistrations.save(pending);
            return challenge(email, transactionId, "REGISTRATION");
        } catch (RuntimeException failure) {
            pendingRegistrations.delete(pending);
            throw failure;
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        User user = userService.lockByEmail(request.getEmail());
        // Temporary safe diagnostics: reports state only, never passwords/hashes/tokens.
        log.info("[CUSTOMER LOGIN] userFound={} role={} active={} verified={} passwordMatches={}",
                user != null, UserService.roleName(user),
                user != null && Boolean.TRUE.equals(user.getIsActive()),
                user != null && Boolean.TRUE.equals(user.getEmailVerified()),
                userService.matchesPassword(user, request.getPassword()));
        if (!UserService.isCustomer(user) || !userService.matchesPassword(user, request.getPassword()))
            throw new AuthFailure(401, "Invalid email or password");
        requireActive(user);
        return challenge(user, otpService.sendEmailOtp(user.getEmail(), OtpService.PURPOSE_LOGIN_EMAIL, user.getName()), "LOGIN");
    }

    @PostMapping({"/login/verify", "/login/otp/verify"})
    public ResponseEntity<?> verifyPasswordLoginOtp(@RequestBody Map<String, String> body) {
        User user = userService.lockByEmail(body.get("email"));
        requireActive(user);
        verify(body, OtpService.PURPOSE_LOGIN_EMAIL);
        return ResponseEntity.ok(userService.toLoginResponse(user));
    }

    // Preserve the legacy paths, but require the challenge issued AFTER password validation.
    @PostMapping({"/login/otp/send", "/login/otp/resend"})
    public ResponseEntity<?> resendLoginOtp(@RequestBody Map<String, String> body) {
        User user = userService.lockByEmail(body.get("email"));
        requireActive(user);
        if (!otpService.hasPendingLoginOtp(user.getEmail(), body.get("transactionId")))
            throw new AuthFailure(403, "Please sign in with your email and password again");
        return challenge(user, otpService.sendEmailOtp(user.getEmail(), OtpService.PURPOSE_LOGIN_EMAIL, user.getName()), "LOGIN");
    }

    private void verify(Map<String, String> body, String purpose) {
        String code = body.getOrDefault("otp", body.get("code"));
        String transaction = body.get("transactionId");
        if (transaction == null || transaction.isBlank()) throw new AuthFailure(400, "OTP session is required");
        if (code == null || !code.matches("[0-9]{6}")) throw new AuthFailure(400, "Invalid OTP");
        if (!otpService.verifyEmailOtp(body.get("email"), code, purpose, transaction)) throw new AuthFailure(400, "Invalid OTP");
    }
    private void requireActive(User user) {
        if (!UserService.isCustomer(user)) throw new AuthFailure(401, "Invalid customer session");
        if (!Boolean.TRUE.equals(user.getIsActive()) || !Boolean.TRUE.equals(user.getEmailVerified()))
            throw new AuthFailure(403, "Please complete email verification");
    }
    private ResponseEntity<?> challenge(String email, String transaction, String purpose) {
        return ResponseEntity.ok(Map.of("success", true, "requiresOtp", true, "otpRequired", true,
            "purpose", purpose, "channel", "email", "email", email, "sentTo", maskEmail(email),
            "transactionId", transaction, "message", "Verification OTP sent"));
    }

    private ResponseEntity<?> challenge(User user, String transaction, String purpose) {
        return challenge(user.getEmail(), transaction, purpose);
    }
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(
            @RequestBody Map<String, String> body
    ) {

        String email =
                OtpService.normalizeEmail(body.get("email"));

        if (email == null || email.isBlank()) {
            return bad("Email is required");
        }

        User user =
                userService.findByEmailNormalized(email);

        if (user != null
                && Boolean.TRUE.equals(user.getIsActive())) {

            try {

                String transactionId =
                        otpService.sendEmailOtp(
                                email,
                                OtpService.PURPOSE_FORGOT_PASSWORD,
                                user.getName()
                        );

                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message",
                        "If an account exists for this email, an OTP has been sent.",
                        "transactionId",
                        transactionId
                ));

            } catch (RuntimeException e) {

                log.error(
                        "Forgot-password OTP failed for {}: {}",
                        maskEmail(email),
                        e.getMessage(),
                        e
                );

                return debugEmailFailure(e);
            }
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message",
                "If an account exists for this email, an OTP has been sent."
        ));
    }

    // =========================================================
    // VERIFY FORGOT PASSWORD OTP
    // =========================================================

    @PostMapping("/forgot-password/verify")
    public ResponseEntity<?> verifyForgotPasswordOtp(
            @RequestBody Map<String, String> body
    ) {

        String email =
                OtpService.normalizeEmail(body.get("email"));

        String otp = body.get("otp");
        String transactionId =
                body.get("transactionId");

        if (email == null || email.isBlank()) {
            return bad("Email is required");
        }

        if (otp == null || otp.isBlank()) {
            return bad("OTP is required");
        }

        if (transactionId == null
                || transactionId.isBlank()) {

            return bad(
                    "Password reset OTP session is required"
            );
        }

        try {

            boolean valid =
                    otpService.verifyEmailOtp(
                            email,
                            otp,
                            OtpService.PURPOSE_FORGOT_PASSWORD,
                            transactionId
                    );

            if (!valid) {
                return bad("Invalid or expired OTP");
            }

        } catch (RuntimeException e) {

            log.error(
                    "Forgot-password OTP verification failed for {}: {}",
                    maskEmail(email),
                    e.getMessage(),
                    e
            );

            return bad(
                    safeMessage(
                            e,
                            "Unable to verify OTP"
                    )
            );
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message",
                "OTP verified. You may now reset your password.",
                "resetAuthorized", true,
                "expiresInMinutes", 10
        ));
    }

    // =========================================================
    // RESET PASSWORD
    // =========================================================

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(
            @RequestBody Map<String, String> body
    ) {

        String email =
                OtpService.normalizeEmail(body.get("email"));

        String otp = body.get("otp");
        String newPassword =
                body.get("newPassword");

        String transactionId =
                body.get("transactionId");

        if (email == null || email.isBlank()) {
            return bad("Email is required");
        }

        if (otp == null || otp.isBlank()) {
            return bad("OTP is required");
        }

        if (newPassword == null
                || newPassword.length() < 6) {

            return bad(
                    "Password must be at least 6 characters"
            );
        }

        if (transactionId == null
                || transactionId.isBlank()
                || !otpService.hasVerifiedForgotPasswordOtp(
                email,
                transactionId
        )) {

            return ResponseEntity
                    .status(403)
                    .body(Map.of(
                            "error",
                            "OTP verification required before resetting the password"
                    ));
        }

        if (!userService.resetPassword(
                email,
                newPassword
        )) {

            return bad(
                    "Account not found or password invalid"
            );
        }

        otpService.consumeForgotPasswordOtp(
                email,
                transactionId
        );

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message",
                "Password reset successfully. You can now sign in."
        ));
    }

    // =========================================================
    // CURRENT USER
    // =========================================================

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(
            Authentication auth
    ) {

        if (auth == null
                || auth.getPrincipal() == null) {

            return ResponseEntity
                    .status(401)
                    .body(Map.of(
                            "error",
                            "Not authenticated"
                    ));
        }

        User user =
                (User) auth.getPrincipal();

        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "name", user.getName(),
                "email", user.getEmail(),
                "phone",
                user.getPhone() == null
                        ? ""
                        : user.getPhone(),
                "role",
                user.getRole() == null
                        ? "customer"
                        : user.getRole().getName(),
                "profileImage",
                user.getProfileImage() == null
                        ? ""
                        : user.getProfileImage(),
                "emailVerified",
                Boolean.TRUE.equals(
                        user.getEmailVerified()
                ),
                "phoneVerified",
                Boolean.TRUE.equals(
                        user.getPhoneVerified()
                )
        ));
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private static ResponseEntity<?> bad(String message) { return ResponseEntity.badRequest().body(Map.of("error", message)); }
    private static ResponseEntity<?> debugEmailFailure(RuntimeException e) {
        if (e instanceof AuthFailure a) return ResponseEntity.status(a.getStatus()).body(Map.of("error", a.getMessage()));
        return ResponseEntity.status(502).body(Map.of("error", "Unable to send OTP email. Please try again."));
    }
    private static String safeMessage(RuntimeException e, String fallback) { return e instanceof AuthFailure ? e.getMessage() : fallback; }
    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "";
        return email.charAt(0) + "***" + email.substring(email.indexOf('@'));
    }
}

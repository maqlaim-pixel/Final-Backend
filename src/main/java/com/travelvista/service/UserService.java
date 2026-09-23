package com.travelvista.service;

import com.travelvista.config.JwtUtil;
import com.travelvista.dto.LoginRequest;
import com.travelvista.dto.LoginResponse;
import com.travelvista.model.Role;
import com.travelvista.model.User;
import com.travelvista.repository.RoleRepository;
import com.travelvista.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    /** Roles allowed into the staff/admin area. This set is the single source of truth. */
    public static final Set<String> ADMIN_ROLE_NAMES = Set.of("admin", "super_admin", "content_manager", "editor");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UserService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Step 1 of login: verify credentials ONLY. No token is issued here —
     * callers must complete an OTP challenge before a JWT is granted.
     */
    public User verifyCredentials(LoginRequest request) {
        if (request == null || request.getEmail() == null || request.getPassword() == null) {
            log.info("[ADMIN LOGIN] userFound=false reason=missing-credentials");
            return null;
        }
        User user = findByEmailNormalized(request.getEmail());
        if (user == null) {
            log.info("[ADMIN LOGIN] userFound=false email={}", maskEmail(request.getEmail()));
            return null;
        }
        // Temporary safe diagnostics: state only — never passwords, hashes, OTPs or tokens.
        boolean passwordMatches = user.getPasswordHash() != null
                && passwordEncoder.matches(request.getPassword(), user.getPasswordHash());
        log.info("[ADMIN LOGIN] userFound=true role={} active={} verified={} passwordMatches={} email={}",
                roleName(user), Boolean.TRUE.equals(user.getIsActive()),
                Boolean.TRUE.equals(user.getEmailVerified()), passwordMatches, maskEmail(user.getEmail()));
        if (!Boolean.TRUE.equals(user.getIsActive()) || !passwordMatches) {
            return null;
        }
        return user;
    }

    /** Role name of a user, or "none" when the account has no role row attached. */
    public static String roleName(User user) {
        if (user == null || user.getRole() == null || user.getRole().getName() == null) return "none";
        return user.getRole().getName();
    }

    /**
     * Stored role names are compared case/whitespace-insensitively so that legacy data
     * variance cannot block a legitimate role. The accepted role set is unchanged.
     */
    public static String normalizeRole(String role) {
        return role == null ? "" : role.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String maskEmail(String email) {
        if (email == null) return "";
        int at = email.indexOf('@');
        if (at <= 1) return email;
        return email.charAt(0) + "*****" + email.substring(at);
    }

    /** Verify only the password, allowing the customer controller to return a precise inactive-account error. */
    public boolean matchesPassword(User user, String rawPassword) {
        return user != null
                && rawPassword != null
                && user.getPasswordHash() != null
                && passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    /** Build the standard JWT login response for a fully-authenticated user. */
    public LoginResponse toLoginResponse(User user) {
        String roleName = user.getRole() != null ? user.getRole().getName() : "user";
        String token = jwtUtil.generateToken(user.getEmail(), roleName, user.getName());
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                roleName,
                user.getProfileImage()
        );
        return new LoginResponse(token, userInfo);
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }

    /**
     * Case-insensitive-tolerant lookup: try the normalized (lowercase) email
     * first, then the exact value. Handles accounts registered before email
     * normalization was introduced.
     */
    public User findByEmailNormalized(String email) {
        String normalized = OtpService.normalizeEmail(email);
        return normalized == null ? null : userRepository.findByEmailIgnoreCase(normalized).orElse(null);
    }

    public User lockByEmail(String email) {
        return userRepository.lockByEmail(OtpService.normalizeEmail(email)).orElse(null);
    }

    public static boolean isCustomer(User user) {
        return user != null && "customer".equals(normalizeRole(roleName(user)));
    }

    /** True when the account is attached to a staff/admin role. */
    public static boolean isStaff(User user) {
        return user != null && ADMIN_ROLE_NAMES.contains(normalizeRole(roleName(user)));
    }

    public static String normalizePhone(String raw) {
        if (raw == null || raw.isBlank()) throw new AuthFailure(400, "Phone number is required");
        String value = raw.trim().replaceAll("[\\s()\\-]", "");
        if (value.startsWith("00")) value = "+" + value.substring(2);
        if (value.matches("[0-9]{10}")) value = "+91" + value;
        else if (value.matches("0[0-9]{10}")) value = "+91" + value.substring(1);
        else if (value.matches("91[0-9]{10}")) value = "+" + value;
        if (!value.matches("\\+[1-9][0-9]{7,14}")) throw new AuthFailure(400, "Please enter a valid phone number with country code");
        return value;
    }

    public void checkPhoneAvailable(String phone, User current) {
        User owner = findByPhone(phone);
        if (owner != null && (current == null || !owner.getId().equals(current.getId())))
            throw new AuthFailure(400, "Phone number is already registered");
    }

    public User findByPhone(String phone) {
        return userRepository.findByPhone(phone).orElse(null);
    }

    public User save(User user) {
        return userRepository.save(user);
    }

    /**
     * Public registration is never allowed to preserve or accept a caller-selected role.
     * Always resolve the existing customer role from the database for new/recovered users.
     */
    public void enforceCustomerRole(User user) {
        if (user == null) {
            throw new AuthFailure(400, "Registration account is required");
        }
        Role customerRole = roleRepository.findByName("customer")
                .orElseThrow(() -> new AuthFailure(500, "Customer role is not configured"));
        user.setRole(customerRole);
    }

    /** Hash a raw password with the shared encoder (used when refreshing a pending registration). */
    public String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    public void delete(User user) {
        userRepository.delete(user);
    }

    /**
     * Login after an email OTP has been verified.
     */
    public LoginResponse loginWithOtp(String email, String phone, String channel) {
        User user;
        if ("phone".equals(channel)) {
            user = userRepository.findByPhone(phone).orElse(null);
        } else {
            user = findByEmailNormalized(email);
        }

        if (user == null || !user.getIsActive()) {
            return null;
        }

        return toLoginResponse(user);
    }

    /**
     * Reset the password after a verified forgot-password OTP.
     * The caller must have checked OtpService.hasVerifiedForgotPasswordOtp
     * before invoking this, and must consume the OTP afterwards.
     */
    public boolean resetPassword(String email, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            return false;
        }
        User user = findByEmailNormalized(email);
        if (user == null || !user.getIsActive()) {
            return false;
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(java.time.LocalDateTime.now());
        userRepository.save(user);
        return true;
    }

    /**
     * Create the customer account but do NOT issue a token — the caller must
     * verify the account's email OTP first (registration challenge).
     */
    public User createCustomer(String name, String email, String phone, String password) {
        Role customerRole = roleRepository.findByName("customer")
                .orElseGet(() -> roleRepository.save(new Role("customer", "Regular website user")));

        checkPhoneAvailable(normalizePhone(phone), null);
        if (findByEmailNormalized(email) != null) throw new AuthFailure(400, "Email is already registered");
        User user = new User(name, OtpService.normalizeEmail(email), normalizePhone(phone), passwordEncoder.encode(password), customerRole);
        // Keep the account inactive until the registration email OTP is verified.
        user.setIsActive(false);
        if (email != null) user.setEmailVerified(false);
        return userRepository.save(user);
    }
}

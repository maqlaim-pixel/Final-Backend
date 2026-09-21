package com.travelvista.service;

import com.travelvista.model.OtpVerification;
import com.travelvista.repository.OtpVerificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Central OTP orchestration. Authentication OTPs are email-only. */
@Service
public class OtpService {
    private final BCryptPasswordEncoder otpEncoder = new BCryptPasswordEncoder();

    public static final String PURPOSE_REGISTER_EMAIL = "register_email";
    public static final String PURPOSE_LOGIN_EMAIL = "login_email";
    public static final String PURPOSE_FORGOT_PASSWORD = "forgot_password";

    /** Kept as a source-compatible alias for older callers; it is email-backed now. */
    public static final String PURPOSE_REGISTER_PHONE = PURPOSE_REGISTER_EMAIL;
    public static final String PURPOSE_LOGIN_PHONE = PURPOSE_LOGIN_EMAIL;

    private static final String AUTH_RECORD_TYPE = "auth";
    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final int MAX_OTPS_PER_HOUR = 10;
    private static final long RESEND_COOLDOWN_SECONDS = 60;

    private final OtpVerificationRepository otpRepo;
    private final EmailOtpService emailOtpService;
    private final ConcurrentMap<String, Long> lastSendAt = new ConcurrentHashMap<>();

    public OtpService(OtpVerificationRepository otpRepo, EmailOtpService emailOtpService) {
        this.otpRepo = otpRepo;
        this.emailOtpService = emailOtpService;
    }

    // Legacy enquiry/lead OTP flows.
    public OtpVerification generateAndSendOtp(String email, String purpose, Long recordId, String recordType) {
        String normalized = normalizeEmail(email);
        checkResendCooldown(normalized, purpose);
        rateLimit(normalized, purpose);
        String code = emailOtpService.generateOtp();
        OtpVerification otp = newHashedOtp(normalized, code, purpose, recordId, recordType, null);
        otpRepo.save(otp);
        emailOtpService.sendOtpEmail(normalized, code, null);
        markSent(normalized, purpose);
        return otp;
    }

    public boolean verifyOtp(String email, String purpose, Long recordId, String recordType, String code) {
        Optional<OtpVerification> optOtp = otpRepo.findTopByEmailAndPurposeAndRecordTypeAndRecordIdOrderByCreatedAtDesc(
                normalizeEmail(email), purpose, recordType, recordId);
        if (optOtp.isEmpty()) return false;
        OtpVerification otp = optOtp.get();
        if (Boolean.TRUE.equals(otp.getVerified())) return false;
        validateAttemptWindow(otp);
        otp.setAttempts(otp.getAttempts() + 1);
        boolean ok = matches(otp, code);
        if (ok) otp.setVerified(true);
        otpRepo.save(otp);
        return ok;
    }

    public boolean hasVerifiedOtp(String email, String purpose, Long recordId, String recordType) {
        Optional<OtpVerification> optOtp = otpRepo.findTopByEmailAndPurposeAndRecordTypeAndRecordIdOrderByCreatedAtDesc(
                normalizeEmail(email), purpose, recordType, recordId);
        return optOtp.isPresent() && Boolean.TRUE.equals(optOtp.get().getVerified()) && !optOtp.get().isExpired();
    }

    public void invalidateOtps(String email, String purpose, Long recordId, String recordType) {
        var otps = otpRepo.findByEmailAndPurposeAndRecordTypeAndRecordIdAndVerifiedFalse(
                normalizeEmail(email), purpose, recordType, recordId);
        for (OtpVerification otp : otps) {
            otp.setVerified(true);
            otpRepo.save(otp);
        }
    }

    // Authentication OTPs: every authentication challenge is sent to email.
    @Transactional(noRollbackFor = AuthFailure.class)
    public String sendAuthOtp(String channel, String email, String phone, String userName, String purpose) {
        String identifier = normalizeEmail(email);
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }

        checkResendCooldown(identifier, purpose);
        rateLimit(identifier, purpose);

        String transactionId = UUID.randomUUID().toString();
        String code = emailOtpService.generateOtp();
        // Delivery failure must not invalidate the previous challenge or authorize a new one.
        if (!emailOtpService.sendOtpEmail(identifier, code, userName))
            throw new AuthFailure(502, "Unable to send OTP email. Please try again.");
        for (OtpVerification old : otpRepo.findByEmailAndPurposeAndVerifiedFalse(identifier, purpose)) {
            old.setVerified(true);
            old.setExpiresAt(LocalDateTime.now());
            otpRepo.save(old);
        }
        otpRepo.save(newHashedOtp(identifier, code, purpose, 0L, AUTH_RECORD_TYPE, transactionId));
        markSent(identifier, purpose);
        return transactionId;
    }

    @Transactional(noRollbackFor = AuthFailure.class)
    public String sendEmailOtp(String email, String purpose, String userName) {
        return sendAuthOtp("email", email, null, userName, purpose);
    }

    public String sendAuthOtp(String channel, String email, String phone, String userName) {
        return sendAuthOtp("email", email, null, userName, PURPOSE_LOGIN_EMAIL);
    }

    /** Channel is accepted for compatibility, but authentication is always email-backed. */
    public boolean verifyAuthOtp(String channel, String email, String phone, String code,
                                 String purpose, String transactionId) {
        return verifyEmailOtp(email, code, purpose, transactionId);
    }

    public boolean verifyAuthOtp(String channel, String email, String phone, String code) {
        return verifyEmailOtp(email, code, PURPOSE_LOGIN_EMAIL, null);
    }

    @Transactional(noRollbackFor = AuthFailure.class)
    public boolean verifyEmailOtp(String email, String code, String purpose, String transactionId) {
        String normalized = normalizeEmail(email);
        Optional<OtpVerification> opt = transactionId == null
                ? otpRepo.findTopByEmailAndPurposeAndRecordTypeOrderByCreatedAtDesc(normalized, purpose, AUTH_RECORD_TYPE)
                : otpRepo.findTopByEmailAndPurposeAndRecordTypeAndTransactionIdOrderByCreatedAtDesc(
                        normalized, purpose, AUTH_RECORD_TYPE, transactionId);
        if (opt.isEmpty()) return false;

        OtpVerification otp = opt.get();
        if (Boolean.TRUE.equals(otp.getVerified())) return false;
        validateAttemptWindow(otp);
        otp.setAttempts(otp.getAttempts() + 1);
        boolean ok = matches(otp, code);
        if (ok) otp.setVerified(true);
        otpRepo.save(otp);
        return ok;
    }

    public boolean verifyEmailOtp(String email, String code, String purpose) {
        return verifyEmailOtp(email, code, purpose, null);
    }

    public boolean hasPendingLoginOtp(String email, String transactionId) {
        if (transactionId == null || transactionId.isBlank()) return false;
        return otpRepo.findTopByEmailAndPurposeAndRecordTypeAndTransactionIdOrderByCreatedAtDesc(
                normalizeEmail(email), PURPOSE_LOGIN_EMAIL, AUTH_RECORD_TYPE, transactionId)
                .filter(otp -> !Boolean.TRUE.equals(otp.getVerified()) && !otp.isExpired()).isPresent();
    }

    public boolean hasVerifiedForgotPasswordOtp(String email, String transactionId) {
        Optional<OtpVerification> opt = otpRepo
                .findTopByEmailAndPurposeAndRecordTypeAndTransactionIdOrderByCreatedAtDesc(
                        normalizeEmail(email), PURPOSE_FORGOT_PASSWORD, AUTH_RECORD_TYPE, transactionId);
        return opt.isPresent() && Boolean.TRUE.equals(opt.get().getVerified()) && !opt.get().isExpired();
    }

    public void consumeForgotPasswordOtp(String email, String transactionId) {
        Optional<OtpVerification> opt = otpRepo
                .findTopByEmailAndPurposeAndRecordTypeAndTransactionIdOrderByCreatedAtDesc(
                        normalizeEmail(email), PURPOSE_FORGOT_PASSWORD, AUTH_RECORD_TYPE, transactionId);
        if (opt.isPresent()) {
            OtpVerification otp = opt.get();
            otp.setVerified(true);
            otp.setExpiresAt(LocalDateTime.now());
            otpRepo.save(otp);
        }
    }

    public boolean hasVerifiedForgotPasswordOtp(String email) {
        Optional<OtpVerification> opt = otpRepo.findTopByEmailAndPurposeAndRecordTypeOrderByCreatedAtDesc(
                normalizeEmail(email), PURPOSE_FORGOT_PASSWORD, AUTH_RECORD_TYPE);
        return opt.isPresent() && Boolean.TRUE.equals(opt.get().getVerified()) && !opt.get().isExpired();
    }

    public void consumeForgotPasswordOtp(String email) {
        List<OtpVerification> otps = otpRepo.findByEmailAndPurposeAndVerifiedFalse(
                normalizeEmail(email), PURPOSE_FORGOT_PASSWORD);
        for (OtpVerification otp : otps) {
            otp.setVerified(true);
            otpRepo.save(otp);
        }
    }

    private OtpVerification newHashedOtp(String identifier, String code, String purpose,
                                         Long recordId, String recordType, String transactionId) {
        OtpVerification otp = new OtpVerification();
        otp.setEmail(identifier);
        otp.setCode("");
        otp.setCodeHash(otpEncoder.encode(code));
        otp.setPurpose(purpose);
        otp.setRecordId(recordId);
        otp.setRecordType(recordType);
        otp.setTransactionId(transactionId);
        otp.setCreatedAt(LocalDateTime.now());
        otp.setExpiresAt(otp.getCreatedAt().plusMinutes(OTP_EXPIRY_MINUTES));
        return otp;
    }

    private boolean matches(OtpVerification otp, String code) {
        if (!AUTH_RECORD_TYPE.equals(otp.getRecordType()) && otp.getCodeHash() != null && !otp.getCodeHash().startsWith("$2"))
            return otp.getCodeHash().equals(EmailOtpService.hashOtp(code, otp.getEmail()));
        return code != null && otp.getCodeHash() != null && otp.getCodeHash().startsWith("$2")
                && otpEncoder.matches(code, otp.getCodeHash());
    }

    private void validateAttemptWindow(OtpVerification otp) {
        if (otp.isMaxAttemptsExceeded()) {
            throw new AuthFailure(429, "Too many failed attempts. Please request a new OTP.");
        }
        if (otp.isExpired()) {
            throw new AuthFailure(400, "OTP expired. Please request a new OTP.");
        }
    }

    private void checkResendCooldown(String identifier, String purpose) {
        otpRepo.findTopByEmailAndPurposeAndRecordTypeOrderByCreatedAtDesc(identifier, purpose, AUTH_RECORD_TYPE)
                .ifPresent(otp -> {
                    if (otp.getCreatedAt().plusSeconds(RESEND_COOLDOWN_SECONDS).isAfter(LocalDateTime.now()))
                        throw new AuthFailure(429, "Please wait before requesting another OTP");
                });
        Long last = lastSendAt.get(identifier + ":" + purpose);
        if (last != null) {
            long elapsed = System.currentTimeMillis() / 1000 - last;
            if (elapsed < RESEND_COOLDOWN_SECONDS) {
                throw new AuthFailure(429, "Please wait " + (RESEND_COOLDOWN_SECONDS - elapsed)
                        + "s before requesting another OTP.");
            }
        }
    }

    private void markSent(String identifier, String purpose) {
        lastSendAt.put(identifier + ":" + purpose, System.currentTimeMillis() / 1000);
    }

    private void rateLimit(String identifier, String purpose) {
        long recent = otpRepo.countRecentByEmailAndPurpose(identifier, purpose, LocalDateTime.now().minusHours(1));
        if (recent >= MAX_OTPS_PER_HOUR) {
            throw new AuthFailure(429, "Too many OTP requests. Please try again later.");
        }
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }
}

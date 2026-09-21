package com.travelvista.service;

import com.twilio.Twilio;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * Phone OTP via Twilio Verify v2.
 *
 * Twilio generates, delivers, expires and throttles the code itself — no phone
 * OTP is stored in our database. Verification is successful only when Twilio
 * returns status "approved".
 *
 * Configure (env vars on Railway):
 *   TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, TWILIO_VERIFY_SERVICE_SID
 */
@Service
public class TwilioVerifyService {

    @Value("${twilio.account-sid:}")
    private String accountSid;

    @Value("${twilio.auth-token:}")
    private String authToken;

    @Value("${twilio.verify-service-sid:}")
    private String verifyServiceSid;

    private volatile boolean initialized = false;

    @PostConstruct
    void init() {
        if (accountSid != null && !accountSid.isBlank()
                && authToken != null && !authToken.isBlank()) {
            Twilio.init(accountSid, authToken);
            initialized = true;
        }
    }

    public boolean isConfigured() {
        return initialized && verifyServiceSid != null && !verifyServiceSid.isBlank();
    }

    /** Send an SMS OTP via Twilio Verify. */
    public boolean sendOtp(String e164Phone) {
        requireConfigured();
        Verification verification = Verification.creator(verifyServiceSid, e164Phone, "sms").create();
        return verification.getStatus() != null; // "pending" on success
    }

    /**
     * Check an OTP. Returns true only for Twilio status "approved".
     * Twilio deletes the verification record on success, which also gives
     * one-time-use semantics for free.
     */
    public boolean verifyOtp(String e164Phone, String code) {
        requireConfigured();
        VerificationCheck check = VerificationCheck.creator(verifyServiceSid)
                .setTo(e164Phone)
                .setCode(code)
                .create();
        return "approved".equals(check.getStatus());
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "Phone OTP is not configured: set TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN and TWILIO_VERIFY_SERVICE_SID");
        }
    }

    // ------------------------------------------------------------------
    // Centralized phone normalization — the ONLY formatting logic for
    // phone numbers in this codebase. Accepts 9876543210 (assumes +91),
    // 09876543210, +919876543210, 919876543210, +91 98765 43210.
    // ------------------------------------------------------------------

    /**
     * Normalize to strict E.164 (+91XXXXXXXXXX for Indian numbers).
     * Only +91 numbers are supported for phone OTP (Twilio trial accounts can
     * only deliver to verified numbers anyway).
     */
    public static String normalizePhone(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Phone number is required");
        }
        String d = raw.replaceAll("[\\s\\-()+]", "");
        if (d.startsWith("00")) d = d.substring(2);
        if (d.length() == 10 && d.matches("\\d{10}"))          return "+91" + d;
        if (d.length() == 11 && d.startsWith("0"))             return "+91" + d.substring(1);
        if (d.length() == 12 && d.startsWith("91"))            return "+" + d;
        if (d.length() == 13 && d.startsWith("+91"))           return d;
        if (d.startsWith("+") && d.length() >= 8 && d.matches("\\+\\d{7,15}")) return d;
        throw new IllegalArgumentException(
                "Invalid phone number. Use a 10-digit Indian mobile (9876543210) or +91XXXXXXXXXX");
    }
}

package com.travelvista.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure JVM tests — no Spring context required.
 */
class OtpProvidersTest {

    // ── Phone normalization (centralized in TwilioVerifyService) ──

    @Test
    void normalizesPlainIndianMobile() {
        assertEquals("+919876543210", TwilioVerifyService.normalizePhone("9876543210"));
    }

    @Test
    void normalizesIndianMobileWithCountryCode() {
        assertEquals("+919876543210", TwilioVerifyService.normalizePhone("+919876543210"));
        assertEquals("+919876543210", TwilioVerifyService.normalizePhone("919876543210"));
    }

    @Test
    void normalizesIndianMobileWithZeroPrefix() {
        assertEquals("+919876543210", TwilioVerifyService.normalizePhone("09876543210"));
    }

    @Test
    void normalizesPhoneWithSpacesAndDashes() {
        assertEquals("+919876543210", TwilioVerifyService.normalizePhone("+91 98765-43210"));
    }

    @Test
    void rejectsEmptyAndNullPhone() {
        assertThrows(IllegalArgumentException.class, () -> TwilioVerifyService.normalizePhone(null));
        assertThrows(IllegalArgumentException.class, () -> TwilioVerifyService.normalizePhone(""));
        assertThrows(IllegalArgumentException.class, () -> TwilioVerifyService.normalizePhone("   "));
    }

    @Test
    void rejectsTooShortOrGarbagePhone() {
        assertThrows(IllegalArgumentException.class, () -> TwilioVerifyService.normalizePhone("12345"));
        assertThrows(IllegalArgumentException.class, () -> TwilioVerifyService.normalizePhone("abcdefghij"));
        assertThrows(IllegalArgumentException.class, () -> TwilioVerifyService.normalizePhone("+91123"));
        assertThrows(IllegalArgumentException.class, () -> TwilioVerifyService.normalizePhone("+1 555"));
    }

    @Test
    void doublePlusStillNormalizesBecausePlusIsFormatting() {
        assertEquals("+919876543210", TwilioVerifyService.normalizePhone("++91 98765 43210"));
    }

    // ── Email OTP generation + hashing ──

    @Test
    void generatesSixDigitOtp() {
        EmailOtpService service = new EmailOtpService();
        for (int i = 0; i < 100; i++) {
            String otp = service.generateOtp();
            assertTrue(otp.matches("\\d{6}"), "OTP must be 6 digits: " + otp);
            int value = Integer.parseInt(otp);
            assertTrue(value >= 100000 && value <= 999999);
        }
    }

    @Test
    void generatesDifferentOtps() {
        EmailOtpService service = new EmailOtpService();
        assertNotEquals(service.generateOtp(), service.generateOtp());
    }

    @Test
    void hashIsDeterministicAndCaseInsensitiveToEmail() {
        String a = EmailOtpService.hashOtp("123456", "User@Example.com");
        String b = EmailOtpService.hashOtp("123456", "user@example.com");
        assertEquals(a, b);
        assertEquals(64, a.length()); // SHA-256 hex
    }

    @Test
    void hashChangesWithDifferentOtpOrEmail() {
        assertNotEquals(EmailOtpService.hashOtp("123456", "a@x.com"), EmailOtpService.hashOtp("123457", "a@x.com"));
        assertNotEquals(EmailOtpService.hashOtp("123456", "a@x.com"), EmailOtpService.hashOtp("123456", "b@x.com"));
    }

    @Test
    void emailNormalizationIsLowercasedAndTrimmed() {
        assertEquals("user@example.com", OtpService.normalizeEmail("  User@Example.COM "));
    }

    // ── Email template ──

    @Test
    void templateContainsOtpAndRequiredPhrases() {
        String html = EmailOtpService.buildHtml("123456", "Ravi");
        assertTrue(html.contains("123456"), "template must embed the OTP");
        assertTrue(html.contains("Your OTP Verification Code") || html.contains("TravelVista"));
        assertTrue(html.contains("10 minutes"));
        assertTrue(html.contains("Never share this OTP"));
        assertTrue(html.contains("Hello Ravi"));
    }

    @Test
    void templateHandlesMissingUserName() {
        String html = EmailOtpService.buildHtml("654321", null);
        assertTrue(html.contains("Hello,"));
        assertTrue(html.contains("654321"));
    }

    @Test
    void templateEscapesUserName() {
        String html = EmailOtpService.buildHtml("111111", "<b>evil</b>");
        assertTrue(html.contains("&lt;b&gt;evil&lt;/b&gt;"));
    }
}

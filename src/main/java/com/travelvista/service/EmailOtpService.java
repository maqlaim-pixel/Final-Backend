package com.travelvista.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Email OTP delivery via the Resend HTTP API (https://api.resend.com/emails).
 * Plain JDK HttpClient + Jackson — no SMTP, no mail starter dependency.
 *
 * OtpService stores a salted BCrypt hash, never the plaintext code.
 * The OTP itself is generated with
 * java.security.SecureRandom — never Math.random.
 *
 * Configure (env vars on Railway):
 *   RESEND_API_KEY, RESEND_FROM_EMAIL, RESEND_FROM_NAME
 */
@Service
public class EmailOtpService {

    private static final Logger log = LoggerFactory.getLogger(EmailOtpService.class);
    private static final String RESEND_URL = "https://api.resend.com/emails";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Value("${resend.api-key:}")
    private String apiKey;

    @Value("${resend.from-email:}")
    private String fromEmail;

    @Value("${resend.from-name:TravelVista}")
    private String fromName;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && fromEmail != null && !fromEmail.isBlank();
    }

    /** Generate a cryptographically secure 6-digit OTP (100000-999999). */
    public String generateOtp() {
        return String.valueOf(100000 + SECURE_RANDOM.nextInt(900000));
    }

    /**
     * SHA-256 of (otp + identifier). Legacy hash helper only; new OTP records use salted BCrypt.
     */
    public static String hashOtp(String otp, String identifier) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(((otp == null ? "" : otp) + ":" + (identifier == null ? "" : identifier.toLowerCase()))
                    .getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(HEX[(b >> 4) & 0x0f]).append(HEX[b & 0x0f]);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Send the OTP email through Resend.
     *
     * @param toEmail   recipient (already normalized)
     * @param otp       the plaintext OTP — used ONLY here to build the mail
     * @param userName  display name for personalization (nullable)
     * @return true when Resend accepts the message (HTTP 2xx)
     */
    public boolean sendOtpEmail(String toEmail, String otp, String userName) {
        if (!isConfigured()) {
            log.warn("Resend is not configured: apiKeyPresent={}, fromEmailPresent={}",
                    apiKey != null && !apiKey.isBlank(), fromEmail != null && !fromEmail.isBlank());
            throw new AuthFailure(502, "Unable to send OTP email. Please contact support or try again later.");
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            String from = (fromName == null || fromName.isBlank())
                    ? fromEmail
                    : fromName + " <" + fromEmail + ">";
            payload.put("from", from);
            payload.put("to", new String[]{ toEmail });
            payload.put("subject", "TravelVista Verification Code");
            payload.put("html", buildHtml(otp, userName));

            HttpRequest request = HttpRequest.newBuilder(URI.create(RESEND_URL))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode provider = parseProviderResponse(response.body());
            String providerName = text(provider, "name");
            String providerMessage = text(provider, "message");
            String providerStatus = text(provider, "statusCode");
            String messageId = text(provider, "id");
            String safeFrom = fromEmail == null ? "" : fromEmail.trim();

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Resend accepted OTP email: httpStatus={}, providerStatus={}, providerName={}, messageId={}, from={}",
                        response.statusCode(), providerStatus, providerName, messageId, safeFrom);
                return true;
            }

            // Log only structured provider metadata. Never log response HTML, request
            // JSON, the recipient, the OTP, or the Authorization header.
            log.warn("Resend rejected OTP email: httpStatus={}, providerStatus={}, providerName={}, providerMessage={}, from={}",
                    response.statusCode(), providerStatus, providerName, providerMessage, safeFrom);
            throw new AuthFailure(502, response.statusCode() == 403
                    ? "Email delivery is restricted by the provider. Please contact support to enable this recipient."
                    : "Unable to send OTP email. Please try again.");
        } catch (AuthFailure e) {
            throw e;
        } catch (Exception e) {
            log.error("Resend email request failed before receiving a response: {}", e.getMessage());
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new AuthFailure(502, "Unable to send OTP email. Please try again.");
        }
    }

    private static JsonNode parseProviderResponse(String body) {
        if (body == null || body.isBlank()) return MAPPER.createObjectNode();
        try {
            return MAPPER.readTree(body);
        } catch (Exception ignored) {
            return MAPPER.createObjectNode();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    /**
     * Professional, responsive OTP email template (inline styles only —
     * email clients strip &lt;style&gt; blocks). The OTP is injected at send time,
     * never hardcoded.
     */
    static String buildHtml(String otp, String userName) {
        String greeting = (userName == null || userName.isBlank())
                ? "Hello,"
                : "Hello " + escapeHtml(userName) + ",";
        return "<!DOCTYPE html>\n"
             + "<html lang=\"en\">\n"
             + "<head><meta charset=\"UTF-8\"/>\n"
             + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"/>\n"
             + "<title>Your OTP Verification Code</title></head>\n"
             + "<body style=\"margin:0;padding:0;background-color:#f1f5f9;\">\n"
             + "  <table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#f1f5f9;padding:24px 12px;\">\n"
             + "    <tr><td align=\"center\">\n"
             + "      <table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:520px;background-color:#ffffff;border-radius:14px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.06);\">\n"
             + "        <tr><td style=\"background-color:#0369a1;padding:22px 32px;\">\n"
             + "          <span style=\"color:#ffffff;font-size:20px;font-weight:700;font-family:Arial,Helvetica,sans-serif;\">TravelVista</span>\n"
             + "        </td></tr>\n"
             + "        <tr><td style=\"padding:32px;font-family:Arial,Helvetica,sans-serif;color:#1e293b;\">\n"
             + "          <p style=\"margin:0 0 14px;font-size:16px;\">" + greeting + "</p>\n"
             + "          <p style=\"margin:0 0 22px;font-size:14px;line-height:1.6;color:#475569;\">Use the verification code below to complete your TravelVista verification:</p>\n"
             + "          <div style=\"text-align:center;margin:0 0 22px;\">\n"
             + "            <span style=\"display:inline-block;font-size:34px;font-weight:700;letter-spacing:10px;color:#0c4a6e;background-color:#f0f9ff;border:1px solid #bae6fd;border-radius:12px;padding:16px 26px;font-family:Arial,Helvetica,sans-serif;\">" + otp + "</span>\n"
             + "          </div>\n"
             + "          <p style=\"margin:0 0 10px;font-size:14px;line-height:1.6;color:#475569;\">This code <strong>expires in 10 minutes</strong>.</p>\n"
             + "          <p style=\"margin:0 0 22px;font-size:13px;line-height:1.6;color:#64748b;\">Never share this OTP with anyone. If you did not request this code, you can safely ignore this email.</p>\n"
             + "          <p style=\"margin:0;font-size:13px;color:#94a3b8;\">Need help? Contact our support team anytime.</p>\n"
             + "        </td></tr>\n"
             + "        <tr><td style=\"background-color:#f8fafc;padding:16px 32px;text-align:center;\">\n"
             + "          <span style=\"font-size:12px;color:#94a3b8;font-family:Arial,Helvetica,sans-serif;\">© TravelVista — Explore the World with Confidence</span>\n"
             + "        </td></tr>\n"
             + "      </table>\n"
             + "    </td></tr>\n"
             + "  </table>\n"
             + "</body>\n"
             + "</html>";
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}

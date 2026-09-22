package com.travelvista.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelvista.model.Invoice;
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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class EmailOtpService {
    private static final Logger log = LoggerFactory.getLogger(EmailOtpService.class);
    private static final String RESEND_URL = "https://api.resend.com/emails";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    @Value("${resend.api-key:}") private String apiKey;
    @Value("${resend.from-email:}") private String fromEmail;
    @Value("${resend.from-name:TravelVista}") private String fromName;

    public boolean isConfigured() { return apiKey != null && !apiKey.isBlank() && fromEmail != null && !fromEmail.isBlank(); }
    public String generateOtp() { return String.valueOf(100000 + RANDOM.nextInt(900000)); }

    public static String hashOtp(String otp, String identifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(((otp == null ? "" : otp) + ":" + (identifier == null ? "" : identifier.toLowerCase())).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : digest) result.append(String.format("%02x", b));
            return result.toString();
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 not available", e); }
    }

    public boolean sendOtpEmail(String toEmail, String otp, String userName) {
        if (!isConfigured()) throw new AuthFailure(502, "Unable to send OTP email. Please contact support or try again later.");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", from()); payload.put("to", new String[]{toEmail});
        payload.put("subject", "TravelVista Verification Code"); payload.put("html", buildHtml(otp, userName));
        return post(payload, "OTP email", 403, "Email delivery is restricted by the provider. Please contact support to enable this recipient.") != null;
    }

    public String sendInvoiceEmail(String toEmail, Invoice invoice, byte[] pdf) {
        if (!isConfigured()) throw new AuthFailure(502, "Invoice email is not configured. Please contact support.");
        if (toEmail == null || toEmail.isBlank()) throw new AuthFailure(400, "Invoice recipient email is required");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", from()); payload.put("to", new String[]{toEmail});
        payload.put("subject", "TravelVista Invoice " + invoice.getInvoiceNumber());
        payload.put("html", invoiceHtml(invoice));
        payload.put("attachments", new Object[]{Map.of("filename", safeFilename(invoice) + ".pdf", "content", Base64.getEncoder().encodeToString(pdf))});
        return post(payload, "invoice email", 403, "Invoice email is restricted by the provider. Please enable this recipient or sender domain.");
    }

    private String post(Map<String, Object> payload, String kind, int restrictedStatus, String restrictedMessage) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(RESEND_URL)).timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(payload), StandardCharsets.UTF_8)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode provider = parse(response.body());
            String id = text(provider, "id");
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Resend accepted {}: httpStatus={}, providerName={}, messageId={}, from={}", kind, response.statusCode(), text(provider, "name"), id, fromEmail);
                return id.isBlank() ? "accepted" : id;
            }
            log.warn("Resend rejected {}: httpStatus={}, providerStatus={}, providerName={}, providerMessage={}, from={}", kind, response.statusCode(), text(provider, "statusCode"), text(provider, "name"), text(provider, "message"), fromEmail);
            throw new AuthFailure(502, response.statusCode() == restrictedStatus ? restrictedMessage : "Unable to send email. Please try again.");
        } catch (AuthFailure e) { throw e;
        } catch (Exception e) { if (e instanceof InterruptedException) Thread.currentThread().interrupt(); log.error("Resend {} failed before response: {}", kind, e.getMessage()); throw new AuthFailure(502, "Unable to send email. Please try again."); }
    }

    private String from() { return fromName == null || fromName.isBlank() ? fromEmail : fromName + " <" + fromEmail + ">"; }
    private static String safeFilename(Invoice invoice) { return (invoice.getInvoiceNumber() == null ? "travelvista-invoice" : invoice.getInvoiceNumber()).replaceAll("[^A-Za-z0-9._-]", "-"); }
    private static JsonNode parse(String body) { try { return body == null || body.isBlank() ? MAPPER.createObjectNode() : MAPPER.readTree(body); } catch (Exception e) { return MAPPER.createObjectNode(); } }
    private static String text(JsonNode node, String field) { JsonNode value = node.get(field); return value == null || value.isNull() ? "" : value.asText(""); }
    private static String invoiceHtml(Invoice invoice) {
        String name = escape(invoice.getCustomerName()); String number = escape(invoice.getInvoiceNumber());
        String booking = invoice.getBooking() == null ? "" : escape(invoice.getBooking().getBookingRef());
        return "<div><h2>TravelVista Invoice</h2><p>Hello " + name + ",</p><p>Your invoice <strong>" + number + "</strong> is attached.</p>"
                + (booking.isBlank() ? "" : "<p>Booking reference: <strong>" + booking + "</strong></p>")
                + "<p>Grand total: <strong>INR " + (invoice.getGrandTotal() == null ? "0.00" : invoice.getGrandTotal()) + "</strong></p>"
                + "<p>Status: " + escape(invoice.getStatus()) + "</p><p>Thank you for choosing TravelVista.</p></div>";
    }
    static String buildHtml(String otp, String userName) {
        String greeting = userName == null || userName.isBlank() ? "Hello," : "Hello " + escape(userName) + ",";
        return "<html><head><title>Your OTP Verification Code</title></head><body style=\"font-family:Arial,sans-serif\"><h2>TravelVista</h2><p>" + greeting + "</p><p>Your verification code is:</p>"
                + "<p style=\"font-size:30px;font-weight:bold;letter-spacing:8px\">" + otp + "</p><p>This code expires in 10 minutes.</p><p>Never share this OTP with anyone.</p></body></html>";
    }
    private static String escape(String value) { return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"); }
}

package com.travelvista;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelvista.config.*;
import com.travelvista.controller.AuthController;
import com.travelvista.controller.CustomerAuthController;
import com.travelvista.model.*;
import com.travelvista.repository.*;
import com.travelvista.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Real PostgreSQL + Spring Security + HTTP mappings; email delivery is always mocked.
 * AUTH_TEST_JDBC_URL must point to a disposable schema created by the test runner.
 */
@EnabledIfEnvironmentVariable(named = "AUTH_TEST_JDBC_URL", matches = ".*currentSchema=tv_auth_test_[a-f0-9]+.*")
@SpringBootTest(classes = CustomerAuthenticationIntegrationTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=${AUTH_TEST_JDBC_URL}", "spring.datasource.username=${AUTH_TEST_DB_USER}",
        "spring.datasource.password=${AUTH_TEST_DB_PASSWORD}", "spring.jpa.hibernate.ddl-auto=update",
        "spring.sql.init.mode=never", "spring.config.import=", "jwt.secret=${AUTH_TEST_JWT_SECRET}"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomerAuthenticationIntegrationTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan("com.travelvista.model")
    @EnableJpaRepositories("com.travelvista.repository")
    @Import({CustomerAuthController.class, AuthController.class, UserService.class, OtpService.class,
            JwtUtil.class, JwtAuthFilter.class, SecurityConfig.class, GlobalExceptionHandler.class,
            com.travelvista.controller.TravelEnquiryController.class,
            LocalTravelEnquiryService.class, ContactEnquiryService.class})
    static class TestApp {}

    @Autowired MockMvc mvc;
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired OtpVerificationRepository otps;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtUtil jwt;
    @Autowired JdbcTemplate jdbc;
    @MockBean EmailOtpService emailService;
    private String email;
    private String phone;
    private final String password = "Correct-password-42";
    private final Map<String, String> delivered = new ConcurrentHashMap<>();

    @BeforeAll void migrate() throws Exception {
        assertTrue(Objects.requireNonNull(jdbc.queryForObject("select current_schema()", String.class)).matches("tv_auth_test_[a-f0-9]+"));
        String sql = new String(Objects.requireNonNull(getClass().getResourceAsStream("/db/customer-auth-migration.sql")).readAllBytes(), StandardCharsets.UTF_8);
        jdbc.execute(sql);
        roles.save(new Role("customer", "Customer"));
        roles.save(new Role("admin", "Admin"));
    }
    @BeforeEach void setup() {
        email = UUID.randomUUID() + "@example.com";
        phone = "+91" + String.format("%010d", Math.abs(UUID.randomUUID().getLeastSignificantBits() % 10000000000L));
        when(emailService.generateOtp()).thenAnswer(i -> new EmailOtpService().generateOtp());
        when(emailService.sendOtpEmail(anyString(), anyString(), any())).thenAnswer(i -> {
            delivered.put(i.getArgument(0), i.getArgument(1)); return true;
        });
    }
    Map<String, String> registration() { return Map.of("name", "Test Customer", "email", email, "phone", phone, "password", password); }
    JsonNode postJson(String path, Object body, int status) throws Exception {
        MvcResult result = mvc.perform(post(path).accept("application/json").contentType("application/json").content(json.writeValueAsBytes(body))).andReturn();
        assertEquals(status, result.getResponse().getStatus(), () -> "Unexpected status for " + path);
        return json.readTree(result.getResponse().getContentAsString());
    }
    JsonNode register() throws Exception { return postJson("/api/auth/register", registration(), 200); }
    Map<String, String> verification(JsonNode challenge, String code) {
        return Map.of("email", email, "otp", code, "transactionId", challenge.get("transactionId").asText());
    }
    User account(boolean active, String role) {
        User u = new User("Test", email, phone, encoder.encode(password), roles.findByName(role).orElseThrow());
        u.setIsActive(active); u.setEmailVerified(active); return users.save(u);
    }
    JsonNode login() throws Exception { return postJson("/api/auth/login", Map.of("email", email, "password", password), 200); }
    OtpVerification otp(JsonNode challenge) {
        return otps.findAll().stream().filter(o -> challenge.get("transactionId").asText().equals(o.getTransactionId())).findFirst().orElseThrow();
    }
    void allowResend(JsonNode challenge) {
        OtpVerification o = otp(challenge); o.setCreatedAt(LocalDateTime.now().minusSeconds(61)); otps.save(o);
        // In-memory legacy limiter also exists. Clear it only in tests to simulate elapsed time.
        Object target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(otpService);
        org.springframework.test.util.ReflectionTestUtils.setField(target, "lastSendAt", new ConcurrentHashMap<String, Long>());
    }
    @Autowired OtpService otpService;

    @Test void registrationCreatesInactiveCustomerAndNoToken() throws Exception {
        JsonNode c = register(); User u = users.findByEmail(email).orElseThrow();
        assertFalse(u.getIsActive()); assertFalse(u.getEmailVerified());
        assertTrue(encoder.matches(password, u.getPasswordHash()));
        assertTrue(c.get("requiresOtp").asBoolean()); assertEquals("REGISTRATION", c.get("purpose").asText()); assertFalse(c.has("token"));
        assertEquals("", otp(c).getCode()); assertNotEquals(delivered.get(email), otp(c).getCodeHash());
    }
    @ParameterizedTest @ValueSource(strings = {"REGISTRATION", "LOGIN"})
    void otpExpiresInExactlyTenMinutes(String purpose) throws Exception {
        if (purpose.equals("LOGIN")) account(true, "customer");
        OtpVerification o = otp(purpose.equals("LOGIN") ? login() : register());
        assertEquals(Duration.ofMinutes(10), Duration.between(o.getCreatedAt(), o.getExpiresAt()));
    }
    @ParameterizedTest @ValueSource(strings = {"REGISTRATION", "LOGIN"})
    void wrongOtpFailsAndPersistsAttempt(String purpose) throws Exception {
        if (purpose.equals("LOGIN")) account(true, "customer");
        JsonNode c = purpose.equals("LOGIN") ? login() : register();
        postJson(purpose.equals("LOGIN") ? "/api/auth/login/verify" : "/api/auth/register/verify", verification(c, "000000"), 400);
        assertEquals(1, otp(c).getAttempts()); assertFalse(otp(c).getVerified());
    }
    @ParameterizedTest @ValueSource(strings = {"REGISTRATION", "LOGIN"})
    void expiredOtpFails(String purpose) throws Exception {
        if (purpose.equals("LOGIN")) account(true, "customer");
        JsonNode c = purpose.equals("LOGIN") ? login() : register();
        OtpVerification o = otp(c); o.setExpiresAt(LocalDateTime.now().minusSeconds(1)); otps.save(o);
        JsonNode error = postJson(purpose.equals("LOGIN") ? "/api/auth/login/verify" : "/api/auth/register/verify", verification(c, delivered.get(email)), 400);
        assertTrue(error.get("error").asText().contains("expired"));
    }
    @Test void registrationActivatesAndAuthenticatesOnce() throws Exception {
        JsonNode c = register(); Map<String, String> body = verification(c, delivered.get(email));
        JsonNode response = postJson("/api/auth/register/verify", body, 200);
        assertTrue(jwt.validateToken(response.get("token").asText())); assertTrue(users.findByEmail(email).orElseThrow().getIsActive());
        postJson("/api/auth/register/verify", body, 400);
    }
    @Test void duplicateActiveEmailRejected() throws Exception {
        account(true, "customer");
        Map<String, String> body = new HashMap<>(registration()); body.put("email", "  " + email.toUpperCase(Locale.ROOT) + "  ");
        assertEquals("Email is already registered", postJson("/api/auth/register", body, 400).get("error").asText());
    }
    @Test void duplicateActivePhoneRejected() throws Exception {
        account(true, "customer"); email = UUID.randomUUID() + "@example.com";
        assertEquals("Phone number is already registered", postJson("/api/auth/register", registration(), 400).get("error").asText());
    }
    @Test void pendingRegistrationReusesIdAndRefreshesDetails() throws Exception {
        User pending = account(false, "customer"); JsonNode c = register();
        assertEquals(pending.getId(), users.findByEmail(email).orElseThrow().getId());
        assertFalse(users.findByEmail(email).orElseThrow().getIsActive()); assertTrue(c.get("requiresOtp").asBoolean());
    }
    @Test void pendingRegistrationCannotClaimAnotherPhone() throws Exception {
        account(true, "customer"); String taken = phone;
        email = UUID.randomUUID() + "@example.com"; phone = "+441234567890"; account(false, "customer"); phone = taken;
        postJson("/api/auth/register", registration(), 400);
    }
    @Test void passwordSuccessOnlyIssuesLoginChallenge() throws Exception {
        account(true, "customer"); JsonNode c = login(); assertFalse(c.has("token")); assertEquals("LOGIN", c.get("purpose").asText());
    }
    @Test void wrongPasswordRejectedWithoutEmail() throws Exception {
        account(true, "customer"); postJson("/api/auth/login", Map.of("email", email, "password", "wrong"), 401);
        verify(emailService, never()).sendOtpEmail(any(), any(), any());
    }
    @Test void inactiveCustomerCannotLogin() throws Exception {
        account(false, "customer"); postJson("/api/auth/login", Map.of("email", email, "password", password), 403);
    }
    @Test void loginOtpAuthenticatesOnceWithOneHourJwt() throws Exception {
        account(true, "customer"); JsonNode c = login(); Map<String, String> body = verification(c, delivered.get(email));
        JsonNode res = postJson("/api/auth/login/verify", body, 200); String token = res.get("token").asText();
        JsonNode claims = json.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        assertEquals(3600, claims.get("exp").asLong() - claims.get("iat").asLong());
        assertEquals(200, mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus());
        postJson("/api/auth/login/verify", body, 400);
    }
    @Test void expiredAndTamperedJwtRejectedByBackend() throws Exception {
        account(true, "customer"); String token = jwt.generateToken(email, "customer", "Test", -1000);
        assertFalse(jwt.validateToken(token));
        assertEquals(401, mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus());
        assertEquals(401, mvc.perform(get("/api/auth/me").header("Authorization", "Bearer invalid.signature.token")).andReturn().getResponse().getStatus());
    }
    @Test void customerCannotUseAdminLogin() throws Exception {
        account(true, "customer"); postJson("/api/admin/login", Map.of("email", email, "password", password), 403);
    }
    @Test void adminLoginWorksIndependently() throws Exception {
        account(true, "admin"); JsonNode res = postJson("/api/admin/login", Map.of("email", email, "password", password), 200);
        assertTrue(jwt.validateToken(res.get("token").asText())); verify(emailService, never()).sendOtpEmail(any(), any(), any());
        assertEquals(200, mvc.perform(get("/api/admin/me").header("Authorization", "Bearer " + res.get("token").asText())).andReturn().getResponse().getStatus());
        postJson("/api/auth/login", Map.of("email", email, "password", password), 401);
    }
    @ParameterizedTest @ValueSource(strings = {"REGISTRATION", "LOGIN"})
    void providerFailureNeverAuthenticatesAndRegistrationCanRetry(String purpose) throws Exception {
        if (purpose.equals("LOGIN")) account(true, "customer");
        when(emailService.sendOtpEmail(anyString(), anyString(), any())).thenThrow(new AuthFailure(502, "Unable to send OTP email. Please try again."));
        JsonNode res = postJson(purpose.equals("LOGIN") ? "/api/auth/login" : "/api/auth/register",
                purpose.equals("LOGIN") ? Map.of("email", email, "password", password) : registration(), 502);
        assertFalse(res.has("token")); assertFalse(res.has("requiresOtp"));
        if (purpose.equals("REGISTRATION")) {
            Long id = users.findByEmail(email).orElseThrow().getId(); assertFalse(users.findByEmail(email).orElseThrow().getIsActive());
            doAnswer(i -> { delivered.put(i.getArgument(0), i.getArgument(1)); return true; }).when(emailService).sendOtpEmail(anyString(), anyString(), any());
            register(); assertEquals(id, users.findByEmail(email).orElseThrow().getId());
        }
    }
    @Test void passwordlessSendAndResendAreRejected() throws Exception {
        account(true, "customer");
        postJson("/api/auth/login/otp/send", Map.of("email", email), 403);
        postJson("/api/auth/login/otp/resend", Map.of("email", email, "transactionId", UUID.randomUUID().toString()), 403);
    }
    @Test void purposeAndTransactionCannotBeSwapped() throws Exception {
        JsonNode c = register(); User u = users.findByEmail(email).orElseThrow(); u.setIsActive(true); u.setEmailVerified(true); users.save(u);
        postJson("/api/auth/login/verify", verification(c, delivered.get(email)), 400);
        JsonNode login = login(); Map<String, String> body = new HashMap<>(verification(login, delivered.get(email)));
        body.put("transactionId", UUID.randomUUID().toString()); postJson("/api/auth/login/verify", body, 400);
    }
    @ParameterizedTest @ValueSource(strings = {"REGISTRATION", "LOGIN"})
    void resendThrottledAndOldChallengeInvalidated(String purpose) throws Exception {
        boolean login = purpose.equals("LOGIN"); if (login) account(true, "customer");
        JsonNode c = login ? login() : register(); String old = delivered.get(email);
        String path = login ? "/api/auth/login/otp/resend" : "/api/auth/register/resend";
        Map<String, String> body = Map.of("email", email, "transactionId", c.get("transactionId").asText());
        postJson(path, body, 429); allowResend(c); JsonNode next = postJson(path, body, 200);
        assertNotEquals(c.get("transactionId"), next.get("transactionId"));
        postJson(login ? "/api/auth/login/verify" : "/api/auth/register/verify", verification(c, old), 400);
        postJson(login ? "/api/auth/login/verify" : "/api/auth/register/verify", verification(next, delivered.get(email)), 200);
    }
    @Test void concurrentVerificationHasExactlyOneWinner() throws Exception {
        account(true, "customer"); JsonNode c = login(); String content = json.writeValueAsString(verification(c, delivered.get(email)));
        ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Integer> attempt = () -> { start.await(); return mvc.perform(post("/api/auth/login/verify").accept("application/json").contentType("application/json").content(content)).andReturn().getResponse().getStatus(); };
            Future<Integer> first = pool.submit(attempt), second = pool.submit(attempt); start.countDown();
            assertEquals(Set.of(200, 400), Set.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)));
        } finally { pool.shutdownNow(); }
    }
    @Test void localTravelAndContactEnquiriesPersistAcrossRealHttpRequests() throws Exception {
        // Reproduce the production failure safely: this test runs only in its disposable schema.
        User admin = new User("Test Admin", "admin-" + UUID.randomUUID() + "@example.com", "+1415" + String.format("%07d", Math.abs(UUID.randomUUID().getLeastSignificantBits() % 10000000L)),
                encoder.encode(password), roles.findByName("admin").orElseThrow());
        admin.setIsActive(true); admin.setEmailVerified(true); users.save(admin);
        JsonNode adminLogin = httpJson("POST", "/api/admin/login", Map.of("email", admin.getEmail(), "password", password), null, 200);
        String adminToken = adminLogin.get("token").asText();

        assertTrue(jdbc.queryForObject("select to_regclass(current_schema() || '.local_travel_enquiries') is not null", Boolean.class),
                "Hibernate ddl-auto=update should create the enquiry table on startup");
        assertTrue(jdbc.queryForObject("select to_regclass(current_schema() || '.contact_enquiries') is not null", Boolean.class));
        assertTrue(httpJson("GET", "/api/admin/local-travel-enquiries", null, adminToken, 200).isArray());

        jdbc.execute("DROP TABLE local_travel_enquiries, contact_enquiries");
        JsonNode missingTableResponse = httpJson("GET", "/api/admin/local-travel-enquiries", null, adminToken, 500);
        assertTrue(missingTableResponse.toString().contains("unexpected error"));
        applyEnquiryMigration();
        JsonNode emptyResponse = httpJson("GET", "/api/admin/local-travel-enquiries", null, adminToken, 200);
        assertTrue(emptyResponse.isArray());
        assertEquals(0, emptyResponse.size(), "An empty table must return an empty list, not 500");

        assertColumns("local_travel_enquiries", Set.of("id", "user_id", "service_type", "form_data", "source_url", "status", "admin_notes", "created_at", "updated_at"));
        assertColumns("contact_enquiries", Set.of("id", "user_id", "name", "email", "phone", "subject", "message", "status", "created_at", "updated_at"));

        JsonNode challenge = httpJson("POST", "/api/auth/register", registration(), null, 200);
        JsonNode registration = httpJson("POST", "/api/auth/register/verify", verification(challenge, delivered.get(email)), null, 200);
        assertTrue(jwt.validateToken(registration.get("token").asText()));
        JsonNode loginChallenge = httpJson("POST", "/api/auth/login", Map.of("email", email, "password", password), null, 200);
        JsonNode customerLogin = httpJson("POST", "/api/auth/login/verify", verification(loginChallenge, delivered.get(email)), null, 200);
        String customerToken = customerLogin.get("token").asText();

        Map<String, String> formData = new LinkedHashMap<>();
        formData.put("transferType", "Arrival"); formData.put("airport", "Mumbai Airport");
        formData.put("pickupDate", "2030-06-15"); formData.put("pickupTime", "10:30");
        formData.put("passengers", "2"); formData.put("vehicleType", "Sedan");
        formData.put("flightNumber", "AI123"); formData.put("dropLocation", "Mumbai Central");
        formData.put("specialRequests", "Child seat, please");
        JsonNode localCreate = httpJson("POST", "/api/local-travel/enquiries",
                Map.of("serviceType", "airport-transfer", "formData", formData, "sourceUrl", "/airport-transfer"), customerToken, 201);
        long localId = localCreate.get("id").asLong();
        assertEquals(1, jdbc.queryForObject("select count(*) from local_travel_enquiries where id = ? and user_id = ?", Integer.class,
                localId, users.findByEmail(email).orElseThrow().getId()));
        assertEquals(json.writeValueAsString(formData), jdbc.queryForObject("select form_data from local_travel_enquiries where id = ?", String.class, localId));

        JsonNode firstAdminRefresh = httpJson("GET", "/api/admin/local-travel-enquiries", null, adminToken, 200);
        JsonNode localSummary = findById(firstAdminRefresh, localId);
        assertEquals("NEW", localSummary.get("status").asText());
        JsonNode localDetail = httpJson("GET", "/api/admin/local-travel-enquiries/" + localId, null, adminToken, 200);
        assertEquals(formData, json.convertValue(localDetail.get("formData"), Map.class));
        assertEquals(email, localDetail.get("customerEmail").asText());
        assertEquals("Test Customer", localDetail.get("customerName").asText());
        assertEquals("/airport-transfer", localDetail.get("sourceUrl").asText());
        httpJson("PATCH", "/api/admin/local-travel-enquiries/" + localId,
                Map.of("status", "IN_PROGRESS", "adminNotes", "Called customer"), adminToken, 200);
        JsonNode refreshedLocal = httpJson("GET", "/api/admin/local-travel-enquiries", null, adminToken, 200);
        assertEquals("IN_PROGRESS", findById(refreshedLocal, localId).get("status").asText());
        assertEquals("IN_PROGRESS", jdbc.queryForObject("select status from local_travel_enquiries where id = ?", String.class, localId));

        JsonNode contactCreate = httpJson("POST", "/api/contact-enquiries", Map.of(
                "name", "Guest Contact", "email", "guest@example.com", "phone", "+14155550123",
                "subject", "Airport pickup", "message", "Please call me about a transfer."), null, 201);
        long contactId = contactCreate.get("id").asLong();
        assertEquals(1, jdbc.queryForObject("select count(*) from contact_enquiries where id = ? and user_id is null", Integer.class, contactId));
        JsonNode contactList = httpJson("GET", "/api/admin/contact-enquiries", null, adminToken, 200);
        assertEquals("guest@example.com", findById(contactList, contactId).get("email").asText());
        JsonNode contactDetail = httpJson("GET", "/api/admin/contact-enquiries/" + contactId, null, adminToken, 200);
        assertEquals("Please call me about a transfer.", contactDetail.get("message").asText());
        httpJson("PATCH", "/api/admin/contact-enquiries/" + contactId, Map.of("status", "CONTACTED"), adminToken, 200);
        assertEquals("CONTACTED", findById(httpJson("GET", "/api/admin/contact-enquiries", null, adminToken, 200), contactId).get("status").asText());
        assertEquals("CONTACTED", jdbc.queryForObject("select status from contact_enquiries where id = ?", String.class, contactId));
    }

    private JsonNode httpJson(String method, String path, Object body, String token, int expectedStatus) throws Exception {
        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + path))
                .header("Accept", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        java.net.http.HttpRequest.BodyPublisher content = body == null
                ? java.net.http.HttpRequest.BodyPublishers.noBody()
                : java.net.http.HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body));
        if (body != null) builder.header("Content-Type", "application/json");
        java.net.http.HttpResponse<String> response = java.net.http.HttpClient.newHttpClient()
                .send(builder.method(method, content).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(expectedStatus, response.statusCode(), () -> method + " " + path + " returned " + response.statusCode());
        return response.body() == null || response.body().isBlank() ? json.nullNode() : json.readTree(response.body());
    }

    private JsonNode findById(JsonNode array, long id) {
        for (JsonNode node : array) if (node.path("id").asLong() == id) return node;
        fail("Enquiry " + id + " was missing from the refreshed admin list"); return null;
    }

    private void assertColumns(String table, Set<String> expected) {
        Set<String> actual = new HashSet<>(jdbc.queryForList(
                "select column_name from information_schema.columns where table_schema = current_schema() and table_name = ?", String.class, table));
        assertTrue(actual.containsAll(expected), () -> table + " is missing columns: " + new HashSet<>(expected) {{ removeAll(actual); }});
    }

    private void applyEnquiryMigration() throws Exception {
        String migration = new String(Objects.requireNonNull(getClass().getResourceAsStream("/db/enquiries-migration.sql")).readAllBytes(), StandardCharsets.UTF_8)
                .replaceAll("(?m)^\\s*--[^\\r\\n]*", "");
        for (String statement : migration.split(";")) {
            String sql = statement.trim();
            if (!sql.isEmpty() && !sql.equalsIgnoreCase("BEGIN") && !sql.equalsIgnoreCase("COMMIT")) jdbc.execute(sql);
        }
    }

    @Test void databaseRejectsDuplicateNormalizedEmailAndPhone() {
        account(true, "customer");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("insert into users(name,email,phone,password_hash) values (?,?,?,?)", "duplicate", email.toUpperCase(Locale.ROOT), "+331234567890", "hash"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("insert into users(name,email,phone,password_hash) values (?,?,?,?)", "duplicate", UUID.randomUUID()+"@example.com", phone, "hash"));
    }
}

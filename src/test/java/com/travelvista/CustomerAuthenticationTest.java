package com.travelvista;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelvista.config.GlobalExceptionHandler;
import com.travelvista.config.JwtUtil;
import com.travelvista.controller.AuthController;
import com.travelvista.controller.CustomerAuthController;
import com.travelvista.model.PendingRegistration;
import com.travelvista.model.Role;
import com.travelvista.model.User;
import com.travelvista.repository.OtpVerificationRepository;
import com.travelvista.repository.PendingRegistrationRepository;
import com.travelvista.repository.RoleRepository;
import com.travelvista.repository.UserRepository;
import com.travelvista.service.EmailOtpService;
import com.travelvista.service.OtpService;
import com.travelvista.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class CustomerAuthenticationTest {
    UserRepository users;
    RoleRepository roles;
    OtpVerificationRepository otps;
    PendingRegistrationRepository pendingRepo;
    EmailOtpService delivery;
    OtpService otpService;
    UserService userService;
    JwtUtil jwt;
    MockMvc mvc;
    ObjectMapper json = new ObjectMapper();
    Map<String, User> accounts;
    Map<String, PendingRegistration> pending;
    List<com.travelvista.model.OtpVerification> challenges;
    String deliveredCode;
    String address;
    final String password = "Correct-password-42";
    final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach void setup() {
        users = mock(UserRepository.class); roles = mock(RoleRepository.class);
        otps = mock(OtpVerificationRepository.class); pendingRepo = mock(PendingRegistrationRepository.class);
        address = "new-" + UUID.randomUUID() + "@example.com";
        accounts = new HashMap<>(); pending = new HashMap<>(); challenges = new ArrayList<>();
        jwt = new JwtUtil(); ReflectionTestUtils.setField(jwt, "secret", "test-only-key-" + UUID.randomUUID());
        delivery = mock(EmailOtpService.class); otpService = new OtpService(otps, delivery);
        userService = new UserService(users, roles, encoder, jwt);
        when(users.findByEmailIgnoreCase(any())).thenAnswer(i -> Optional.ofNullable(accounts.get(i.getArgument(0))));
        when(users.lockByEmail(any())).thenAnswer(i -> Optional.ofNullable(accounts.get(i.getArgument(0))));
        when(users.findByPhone(any())).thenAnswer(i -> accounts.values().stream().filter(u -> Objects.equals(u.getPhone(), i.getArgument(0))).findFirst());
        when(users.save(any())).thenAnswer(i -> { User u = i.getArgument(0); if (u.getId() == null) u.setId((long) accounts.size() + 1); accounts.put(u.getEmail(), u); return u; });
        when(roles.findByName("customer")).thenReturn(Optional.of(new Role("customer", "Customer")));
        when(roles.findByName("admin")).thenReturn(Optional.of(new Role("admin", "Admin")));
        when(pendingRepo.findByEmail(any())).thenAnswer(i -> Optional.ofNullable(pending.get(i.getArgument(0))));
        when(pendingRepo.findByEmailAndTransactionId(any(), any())).thenAnswer(i -> Optional.ofNullable(pending.get(i.getArgument(0))
                ).filter(p -> p.getTransactionId().equals(i.getArgument(1))));
        when(pendingRepo.save(any())).thenAnswer(i -> { PendingRegistration p = i.getArgument(0); pending.put(p.getEmail(), p); return p; });
        doAnswer(i -> { pending.remove(((PendingRegistration)i.getArgument(0)).getEmail()); return null; }).when(pendingRepo).delete(any());
        when(otps.save(any())).thenAnswer(i -> { com.travelvista.model.OtpVerification o = i.getArgument(0); if (!challenges.contains(o)) challenges.add(o); return o; });
        when(otps.findByEmailAndPurposeAndVerifiedFalse(any(), any())).thenAnswer(i -> challenges.stream().filter(o -> o.getEmail().equals(i.getArgument(0)) && o.getPurpose().equals(i.getArgument(1)) && !o.getVerified()).toList());
        when(otps.findTopByEmailAndPurposeAndRecordTypeOrderByCreatedAtDesc(any(), any(), any())).thenAnswer(i -> challenges.stream().filter(o -> o.getEmail().equals(i.getArgument(0)) && o.getPurpose().equals(i.getArgument(1))).max(Comparator.comparing(com.travelvista.model.OtpVerification::getCreatedAt)));
        when(otps.findTopByEmailAndPurposeAndRecordTypeAndTransactionIdOrderByCreatedAtDesc(any(), any(), any(), any())).thenAnswer(i -> challenges.stream().filter(o -> o.getEmail().equals(i.getArgument(0)) && o.getPurpose().equals(i.getArgument(1)) && o.getTransactionId().equals(i.getArgument(3))).findFirst());
        when(delivery.generateOtp()).thenReturn("123456");
        when(delivery.sendOtpEmail(any(), any(), any())).thenAnswer(i -> { deliveredCode = i.getArgument(1); return true; });
        mvc = MockMvcBuilders.standaloneSetup(new CustomerAuthController(userService, otpService, pendingRepo), new AuthController(userService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    Map<String, String> registration() { return Map.of("name", "Test Customer", "email", address, "phone", "+919876543210", "password", password); }
    JsonNode post(String path, Object body, int status) throws Exception {
        var result = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).contentType("application/json").accept("application/json").content(json.writeValueAsBytes(body))).andReturn();
        assertEquals(status, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        return json.readTree(result.getResponse().getContentAsString());
    }

    @Test void registerDoesNotCreatePermanentUserBeforeOtp() throws Exception {
        JsonNode challenge = post("/api/auth/register", registration(), 200);
        assertTrue(accounts.isEmpty());
        assertTrue(pending.containsKey(address));
        assertTrue(challenge.get("otpRequired").asBoolean());
        assertFalse(challenge.has("token"));
    }

    @Test void wrongOtpLeavesPermanentUsersEmpty() throws Exception {
        JsonNode challenge = post("/api/auth/register", registration(), 200);
        post("/api/auth/register/verify", Map.of("email", address, "otp", "000000", "transactionId", challenge.get("transactionId").asText()), 400);
        assertTrue(accounts.isEmpty());
    }

    @Test void correctOtpCreatesExactlyOneActiveCustomer() throws Exception {
        JsonNode challenge = post("/api/auth/register", registration(), 200);
        JsonNode response = post("/api/auth/register/verify", Map.of("email", address, "otp", deliveredCode, "transactionId", challenge.get("transactionId").asText()), 200);
        assertEquals(1, accounts.size());
        User created = accounts.get(address);
        assertEquals("customer", created.getRole().getName());
        assertTrue(created.getIsActive());
        assertTrue(created.getEmailVerified());
        assertTrue(response.has("token"));
        assertTrue(pending.isEmpty());
        post("/api/auth/register/verify", Map.of("email", address, "otp", deliveredCode, "transactionId", challenge.get("transactionId").asText()), 400);
    }

    @Test void multipleDifferentRegistrationsRemainIndependent() throws Exception {
        for (int i = 0; i < 2; i++) {
            String address = "customer-" + i + "@example.com";
            String phone = "+9198765432" + String.format("%02d", i);
            when(delivery.sendOtpEmail(eq(address), any(), any())).thenAnswer(invocation -> { deliveredCode = invocation.getArgument(1); return true; });
            JsonNode challenge = post("/api/auth/register", Map.of("name", "Customer " + i, "email", address, "phone", phone, "password", password), 200);
            post("/api/auth/register/verify", Map.of("email", address, "otp", deliveredCode, "transactionId", challenge.get("transactionId").asText()), 200);
        }
        assertEquals(2, accounts.size());
        assertTrue(accounts.values().stream().allMatch(u -> "customer".equals(u.getRole().getName()) && u.getIsActive()));
    }

    @Test void failedDeliveryLeavesNoPermanentUser() throws Exception {
        when(delivery.sendOtpEmail(any(), any(), any())).thenThrow(new com.travelvista.service.AuthFailure(502, "Unable to send OTP email. Please try again."));
        post("/api/auth/register", registration(), 502);
        assertTrue(accounts.isEmpty());
        assertTrue(pending.isEmpty());
    }

    @Test void customerLoginStillRequiresOtpAndAdminRemainsSeparate() throws Exception {
        User user = new User("Active", address, "+919876543210", encoder.encode(password), new Role("customer", "Customer"));
        user.setIsActive(true); user.setEmailVerified(true); users.save(user);
        JsonNode challenge = post("/api/auth/login", Map.of("email", address, "password", password), 200);
        assertEquals("LOGIN", challenge.get("purpose").asText());
        assertFalse(challenge.has("token"));
        post("/api/admin/login", Map.of("email", address, "password", password), 403);
    }
}

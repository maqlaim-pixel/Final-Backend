package com.travelvista;

import com.travelvista.config.*;
import com.travelvista.controller.*;
import com.travelvista.model.*;
import com.travelvista.repository.UserRepository;
import com.travelvista.repository.PendingRegistrationRepository;
import com.travelvista.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(classes=AuthenticationSecurityTest.Config.class, properties="spring.config.import=")
@AutoConfigureMockMvc
class AuthenticationSecurityTest {
    @org.springframework.test.context.DynamicPropertySource
    static void testSecrets(org.springframework.test.context.DynamicPropertyRegistry properties) {
        properties.add("jwt.secret", () -> "test-only-key-" + UUID.randomUUID());
    }
    @Configuration
    @EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class,HibernateJpaAutoConfiguration.class})
    @Import({SecurityConfig.class,JwtAuthFilter.class,AuthController.class,CustomerAuthController.class})
    static class Config {
        @Bean JwtUtil jwtUtil() {
            JwtUtil jwt=new JwtUtil(); ReflectionTestUtils.setField(jwt,"secret","test-only-key-"+UUID.randomUUID()); return jwt;
        }
        @Bean PendingRegistrationRepository pendingRegistrations() {
            return mock(PendingRegistrationRepository.class);
        }
    }
    @MockBean UserRepository users;
    @MockBean UserService service;
    @MockBean OtpService otp;
    @Autowired JwtUtil jwt;
    @Autowired MockMvc mvc;
    User user;
    @BeforeEach void setup() {
        user=new User("Customer","security@example.com","hash",new Role("customer","customer"));
        user.setId(1L); user.setIsActive(true); user.setEmailVerified(true);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }
    int getStatus(String path,String token) throws Exception {
        return mvc.perform(get(path).accept("application/json").header("Authorization","Bearer "+token)).andReturn().getResponse().getStatus();
    }
    @Test void expiredJwtIsRejectedBySecurityFilter() throws Exception {
        assertEquals(401,getStatus("/api/auth/me",jwt.generateToken(user.getEmail(),"customer","Test",-1000)));
    }
    @Test void validCustomerJwtCanReadCustomerMeButNotAdminMe() throws Exception {
        String token=jwt.generateToken(user.getEmail(),"customer","Test");
        assertEquals(200,getStatus("/api/auth/me",token)); assertEquals(403,getStatus("/api/admin/me",token));
    }
    @Test void inactiveOrUnverifiedCustomerJwtIsRejected() throws Exception {
        String token=jwt.generateToken(user.getEmail(),"customer","Test");
        user.setIsActive(false); assertEquals(401,getStatus("/api/auth/me",token));
        user.setIsActive(true); user.setEmailVerified(false); assertEquals(401,getStatus("/api/auth/me",token));
    }
    @Test void adminSessionWorksWithoutCustomerVerificationFlag() throws Exception {
        user.setRole(new Role("admin","admin")); user.setEmailVerified(null);
        assertEquals(200,getStatus("/api/admin/me",jwt.generateToken(user.getEmail(),"admin","Test")));
    }
    @Test void currentDatabaseRoleOverridesOldJwtRole() throws Exception {
        assertEquals(403,getStatus("/api/admin/me",jwt.generateToken(user.getEmail(),"admin","Test")));
    }

    @Test void productionFrontendCanPreflightCredentialedLoginRequest() throws Exception {
        String origin = "https://final-frontend-dhavalmaqlaim-5177.vercel.app";
        var response = mvc.perform(options("/api/auth/login")
                        .header("Origin", origin)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andReturn()
                .getResponse();

        assertEquals(200, response.getStatus());
        assertEquals(origin, response.getHeader("Access-Control-Allow-Origin"));
        assertEquals("true", response.getHeader("Access-Control-Allow-Credentials"));
        assertTrue(response.getHeader("Access-Control-Allow-Methods").contains("POST"));
        assertNotNull(response.getHeader("Access-Control-Allow-Headers"));
    }

    @Test void projectVercelPreviewOriginIsAllowedForPreflight() throws Exception {
        String origin = "https://final-frontend-preview-123-dhavalmaqlaim-5177.vercel.app";
        var response = mvc.perform(options("/api/packages")
                        .header("Origin", origin)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andReturn()
                .getResponse();

        assertEquals(200, response.getStatus());
        assertEquals(origin, response.getHeader("Access-Control-Allow-Origin"));
        assertEquals("true", response.getHeader("Access-Control-Allow-Credentials"));
    }
}

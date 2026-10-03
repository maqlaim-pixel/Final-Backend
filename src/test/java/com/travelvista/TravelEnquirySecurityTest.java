package com.travelvista;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelvista.config.JwtAuthFilter;
import com.travelvista.config.JwtUtil;
import com.travelvista.config.SecurityConfig;
import com.travelvista.controller.TravelEnquiryController;
import com.travelvista.dto.ContactEnquiryRequest;
import com.travelvista.model.ContactEnquiry;
import com.travelvista.model.Role;
import com.travelvista.model.User;
import com.travelvista.repository.UserRepository;
import com.travelvista.service.ContactEnquiryService;
import com.travelvista.service.LocalTravelEnquiryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(classes = TravelEnquirySecurityTest.Config.class, properties = "spring.config.import=")
@AutoConfigureMockMvc
class TravelEnquirySecurityTest {
    private static final String SECRET = "travel-enquiry-security-test-secret-" + UUID.randomUUID();

    @Configuration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
    @Import({SecurityConfig.class, JwtAuthFilter.class, TravelEnquiryController.class})
    static class Config {
        @Bean JwtUtil jwtUtil() {
            JwtUtil jwt = new JwtUtil();
            ReflectionTestUtils.setField(jwt, "secret", SECRET);
            return jwt;
        }
        @Bean LocalTravelEnquiryService localTravelService() { return mock(LocalTravelEnquiryService.class); }
        @Bean ContactEnquiryService contactService() { return mock(ContactEnquiryService.class); }
    }

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwt;
    @MockBean UserRepository users;
    @Autowired LocalTravelEnquiryService localTravelService;
    @Autowired ContactEnquiryService contactService;
    private User customer;

    @BeforeEach void setup() {
        customer = user("customer");
        when(users.findByEmail(customer.getEmail())).thenReturn(Optional.of(customer));
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(user("admin")));
    }

    @Test void localTravelCreationRequiresAuthentication() throws Exception {
        assertEquals(401, mvc.perform(post("/api/local-travel/enquiries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceType\":\"airport-transfer\",\"formData\":{\"pickupDate\":\"2030-01-01\"}}"))
                .andReturn().getResponse().getStatus());
        verify(localTravelService, never()).create(any(), any());
    }

    @Test void customerCannotOpenAdminEnquiryApi() throws Exception {
        String token = jwt.generateToken(customer.getEmail(), "customer", customer.getName());
        assertEquals(403, mvc.perform(get("/api/admin/local-travel-enquiries")
                .header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus());
    }

    @Test void adminEnquiryApiRequiresAdminRole() throws Exception {
        User admin = user("admin");
        String token = jwt.generateToken(admin.getEmail(), "admin", admin.getName());
        when(localTravelService.list(null)).thenReturn(java.util.List.of());
        assertEquals(200, mvc.perform(get("/api/admin/local-travel-enquiries")
                .header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus());
    }

    @Test void contactCreationRemainsPublic() throws Exception {
        ContactEnquiry created = new ContactEnquiry();
        created.setId(9L);
        created.setStatus("NEW");
        when(contactService.create(any(ContactEnquiryRequest.class), isNull())).thenReturn(created);
        int status = mvc.perform(post("/api/contact-enquiries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"A User\",\"email\":\"a@example.com\",\"phone\":\"+919876543210\",\"subject\":\"General Enquiry\",\"message\":\"Hello\"}"))
                .andReturn().getResponse().getStatus();
        assertEquals(201, status);
        verify(contactService).create(any(ContactEnquiryRequest.class), isNull());
    }

    private User user(String role) {
        User user = new User("Test User", role + "@example.com", "hash", new Role(role, role));
        user.setId(4L);
        user.setIsActive(true);
        user.setEmailVerified(true);
        return user;
    }
}

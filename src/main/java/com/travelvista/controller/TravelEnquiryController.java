package com.travelvista.controller;

import com.travelvista.dto.ContactEnquiryRequest;
import com.travelvista.dto.LocalTravelEnquiryRequest;
import com.travelvista.model.ContactEnquiry;
import com.travelvista.model.LocalTravelEnquiry;
import com.travelvista.model.User;
import com.travelvista.repository.UserRepository;
import com.travelvista.service.ContactEnquiryService;
import com.travelvista.service.LocalTravelEnquiryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class TravelEnquiryController {
    private final LocalTravelEnquiryService localTravelService;
    private final ContactEnquiryService contactService;
    private final UserRepository userRepository;

    public TravelEnquiryController(LocalTravelEnquiryService localTravelService,
                                   ContactEnquiryService contactService,
                                   UserRepository userRepository) {
        this.localTravelService = localTravelService;
        this.contactService = contactService;
        this.userRepository = userRepository;
    }

    @PostMapping("/api/local-travel/enquiries")
    public ResponseEntity<?> createLocalTravel(@Valid @RequestBody LocalTravelEnquiryRequest request,
                                               Authentication authentication) {
        User user = authenticatedUser(authentication);
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        LocalTravelEnquiry enquiry = localTravelService.create(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", enquiry.getId(), "status", enquiry.getStatus(), "message", "Local Travel enquiry submitted successfully"));
    }

    @PostMapping("/api/contact-enquiries")
    public ResponseEntity<?> createContact(@Valid @RequestBody ContactEnquiryRequest request, Authentication authentication) {
        ContactEnquiry enquiry = contactService.create(request, authenticatedUser(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", enquiry.getId(), "status", enquiry.getStatus(), "message", "Contact enquiry submitted successfully"));
    }

    @GetMapping("/api/admin/local-travel-enquiries")
    public List<Map<String, Object>> localTravelList(@RequestParam(required = false) String status) {
        return localTravelService.list(status).stream().map(this::localTravelDto).toList();
    }

    @GetMapping("/api/admin/local-travel-enquiries/{id}")
    public Map<String, Object> localTravelDetail(@PathVariable Long id) {
        return localTravelDto(localTravelService.get(id));
    }

    @PatchMapping("/api/admin/local-travel-enquiries/{id}")
    public Map<String, Object> updateLocalTravel(@PathVariable Long id, @RequestBody Map<String, String> updates) {
        return localTravelDto(localTravelService.update(id, updates.get("status"), updates.get("adminNotes")));
    }

    @GetMapping("/api/admin/contact-enquiries")
    public List<Map<String, Object>> contactList(@RequestParam(required = false) String status) {
        return contactService.list(status).stream().map(this::contactDto).toList();
    }

    @GetMapping("/api/admin/contact-enquiries/{id}")
    public Map<String, Object> contactDetail(@PathVariable Long id) {
        return contactDto(contactService.get(id));
    }

    @PatchMapping("/api/admin/contact-enquiries/{id}")
    public Map<String, Object> updateContact(@PathVariable Long id, @RequestBody Map<String, String> updates) {
        return contactDto(contactService.update(id, updates.get("status")));
    }

    private User authenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return null;
        if (authentication.getPrincipal() instanceof User user) return user;
        String email = authentication.getName();
        return email == null ? null : userRepository.findByEmail(email).orElse(null);
    }

    private Map<String, Object> localTravelDto(LocalTravelEnquiry enquiry) {
        User user = enquiry.getUser();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", enquiry.getId());
        response.put("userId", user.getId());
        response.put("customerName", user.getName());
        response.put("customerEmail", user.getEmail());
        response.put("customerPhone", user.getPhone());
        response.put("serviceType", enquiry.getServiceType());
        response.put("formData", localTravelService.readFields(enquiry.getFormData()));
        response.put("sourceUrl", enquiry.getSourceUrl());
        response.put("status", enquiry.getStatus());
        response.put("adminNotes", enquiry.getAdminNotes());
        response.put("createdAt", enquiry.getCreatedAt());
        response.put("updatedAt", enquiry.getUpdatedAt());
        return response;
    }

    private Map<String, Object> contactDto(ContactEnquiry enquiry) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", enquiry.getId());
        response.put("name", enquiry.getName());
        response.put("email", enquiry.getEmail());
        response.put("phone", enquiry.getPhone());
        response.put("subject", enquiry.getSubject());
        response.put("message", enquiry.getMessage());
        response.put("status", enquiry.getStatus());
        response.put("userId", enquiry.getUser() == null ? null : enquiry.getUser().getId());
        response.put("createdAt", enquiry.getCreatedAt());
        response.put("updatedAt", enquiry.getUpdatedAt());
        return response;
    }
}

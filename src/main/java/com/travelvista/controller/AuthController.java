package com.travelvista.controller;

import com.travelvista.dto.LoginRequest;
import com.travelvista.dto.LoginResponse;
import com.travelvista.model.User;
import com.travelvista.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Admin login is intentionally password-only. Customer OTP enforcement is
     * handled by CustomerAuthController; this endpoint is reserved for admins.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        if (request.getEmail() == null || request.getPassword() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email and password are required"));
        }

        User user = userService.verifyCredentials(request);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }

        String role = user.getRole() == null ? "" : user.getRole().getName();
        if (!("admin".equals(role) || "super_admin".equals(role)
                || "content_manager".equals(role) || "editor".equals(role))) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }

        return ResponseEntity.ok(userService.toLoginResponse(user));
    }

    private static String maskEmail(String email) {
        if (email == null) return "";
        int at = email.indexOf('@');
        if (at <= 1) return email;
        return email.charAt(0) + "*****" + email.substring(at);
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "";
        return phone.substring(0, phone.length() - 4).replaceAll(".", "*") + phone.substring(phone.length() - 4);
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        User user = (User) auth.getPrincipal();
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "name", user.getName(),
                "email", user.getEmail(),
                "phone", user.getPhone() != null ? user.getPhone() : "",
                "role", user.getRole() != null ? user.getRole().getName() : "user",
                "profileImage", user.getProfileImage() != null ? user.getProfileImage() : ""
        ));
    }
}

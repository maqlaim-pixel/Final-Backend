package com.travelvista.controller;

import com.travelvista.model.*;
import com.travelvista.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/customer")
public class CustomerController {

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final EnquiryRepository enquiryRepository;
    private final ReviewRepository reviewRepository;

    public CustomerController(UserRepository userRepository, BookingRepository bookingRepository,
                              EnquiryRepository enquiryRepository, ReviewRepository reviewRepository) {
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.enquiryRepository = enquiryRepository;
        this.reviewRepository = reviewRepository;
    }

    private User getUser(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof User) return (User) principal;
        // Fallback: look up by email from JWT
        if (auth.getName() != null) {
            return userRepository.findByEmail(auth.getName()).orElse(null);
        }
        return null;
    }

    // ── Dashboard ──────────────────────────────────────────────
    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard(Authentication auth) {
        User user = getUser(auth);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        long bookingCount = bookingRepository.countByUserId(user.getId());
        List<Booking> recentBookings = bookingRepository.findByUserIdOrderByCreatedAtDesc(user.getId());

        List<Map<String, Object>> recentList = new ArrayList<>();
        for (Booking b : recentBookings) {
            Map<String, Object> bm = new LinkedHashMap<>();
            bm.put("id", b.getId());
            bm.put("bookingRef", b.getBookingRef());
            bm.put("packageName", b.getPackageTitle());
            bm.put("travelDate", b.getTravelDate() != null ? b.getTravelDate().toString() : null);
            bm.put("endDate", b.getEndDate() != null ? b.getEndDate().toString() : null);
            bm.put("travelers", b.getTravelers());
            bm.put("totalAmount", b.getTotalAmount());
            bm.put("status", b.getStatus());
            bm.put("paymentStatus", b.getPaymentStatus());
            bm.put("createdAt", b.getCreatedAt() != null ? b.getCreatedAt().toString() : null);
            recentList.add(bm);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", user.getId());
        response.put("name", user.getName());
        response.put("email", user.getEmail());
        response.put("phone", user.getPhone() != null ? user.getPhone() : "");
        response.put("role", user.getRole() != null ? user.getRole().getName() : "customer");
        response.put("profileImage", user.getProfileImage() != null ? user.getProfileImage() : "");
        response.put("bookingCount", bookingCount);
        long enquiryCount = enquiryRepository.countByUserId(user.getId());
        long pendingEnquiries = enquiryRepository.countByUserIdAndStatus(user.getId(), "pending");
        response.put("enquiryCount", enquiryCount);
        response.put("pendingEnquiries", pendingEnquiries);
        response.put("recentBookings", recentList);

        return ResponseEntity.ok(response);
    }

    // ── Profile ────────────────────────────────────────────────
    @GetMapping("/profile")
    public ResponseEntity<?> profile(Authentication auth) {
        User user = getUser(auth);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "name", user.getName(),
                "email", user.getEmail(),
                "phone", user.getPhone() != null ? user.getPhone() : "",
                "role", user.getRole() != null ? user.getRole().getName() : "customer",
                "profileImage", user.getProfileImage() != null ? user.getProfileImage() : "",
                "createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : null
        ));
    }

    // ── Update Profile ──────────────────────────────────────
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(Authentication auth, @RequestBody Map<String, Object> body) {
        User user = getUser(auth);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        if (body.containsKey("name") && body.get("name") != null) {
            user.setName(body.get("name").toString());
        }
        if (body.containsKey("phone")) {
            String phone = com.travelvista.service.UserService.normalizePhone(
                    body.get("phone") == null ? null : body.get("phone").toString());
            User owner = userRepository.findByPhone(phone).orElse(null);
            if (owner != null && !owner.getId().equals(user.getId()))
                throw new com.travelvista.service.AuthFailure(400, "Phone number is already registered");
            user.setPhone(phone);
        }
        if (body.containsKey("profileImage")) {
            user.setProfileImage(body.get("profileImage") != null ? body.get("profileImage").toString() : null);
        }
        user.setUpdatedAt(java.time.LocalDateTime.now());
        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "id", user.getId(),
            "name", user.getName(),
            "email", user.getEmail(),
            "phone", user.getPhone() != null ? user.getPhone() : "",
            "profileImage", user.getProfileImage() != null ? user.getProfileImage() : "",
            "message", "Profile updated successfully"
        ));
    }

    // ── Bookings ───────────────────────────────────────────────
    @GetMapping("/bookings")
    public ResponseEntity<?> bookings(Authentication auth) {
        User user = getUser(auth);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        List<Booking> bookings = bookingRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        List<Map<String, Object>> list = new ArrayList<>();
        for (Booking b : bookings) {
            Map<String, Object> bm = new LinkedHashMap<>();
            bm.put("id", b.getId());
            bm.put("bookingRef", b.getBookingRef());
            bm.put("packageName", b.getPackageTitle());
            bm.put("travelDate", b.getTravelDate() != null ? b.getTravelDate().toString() : null);
            bm.put("endDate", b.getEndDate() != null ? b.getEndDate().toString() : null);
            bm.put("travelers", b.getTravelers());
            bm.put("totalAmount", b.getTotalAmount());
            bm.put("status", b.getStatus());
            bm.put("paymentStatus", b.getPaymentStatus());
            bm.put("createdAt", b.getCreatedAt() != null ? b.getCreatedAt().toString() : null);
            list.add(bm);
        }
        return ResponseEntity.ok(list);
    }

    private List<String> parseImages(String imagesJson) {
        if (imagesJson == null || imagesJson.isBlank()) return List.of();
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return Arrays.asList(mapper.readValue(imagesJson, String[].class));
        } catch (Exception e) {
            return List.of();
        }
    }

    // ── My Reviews ────────────────────────────────────────────
    @GetMapping("/reviews")
    public ResponseEntity<?> myReviews(Authentication auth) {
        User user = getUser(auth);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        List<Review> reviews = reviewRepository.findAll().stream()
                .filter(r -> r.getUser() != null && r.getUser().getId().equals(user.getId()))
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null) return 1;
                    if (b.getCreatedAt() == null) return -1;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .toList();

        List<Map<String, Object>> list = new ArrayList<>();
        for (Review r : reviews) {
            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("id", r.getId());
            rm.put("rating", r.getRating());
            rm.put("comment", r.getComment());
            rm.put("images", parseImages(r.getImages()));
            rm.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
            if (r.getTravelPackage() != null) {
                rm.put("packageName", r.getTravelPackage().getTitle());
            }
            list.add(rm);
        }
        return ResponseEntity.ok(list);
    }

    // ── Edit Review ──────────────────────────────────────────
    @PutMapping("/reviews/{reviewId}")
    public ResponseEntity<?> editReview(Authentication auth, @PathVariable Long reviewId,
                                         @RequestBody Map<String, Object> body) {
        User user = getUser(auth);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        Optional<Review> reviewOpt = reviewRepository.findById(reviewId);
        if (reviewOpt.isEmpty()) return ResponseEntity.notFound().build();

        Review review = reviewOpt.get();
        if (review.getUser() == null || !review.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "You can only edit your own reviews"));
        }

        if (body.containsKey("rating") && body.get("rating") != null) {
            review.setRating(Math.min(5, Math.max(1, Integer.valueOf(body.get("rating").toString()))));
        }
        if (body.containsKey("comment") && body.get("comment") != null) {
            String comment = body.get("comment").toString().trim();
            if (comment.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Review comment is required"));
            }
            review.setComment(comment);
        }
        if (body.containsKey("images")) {
            Object imagesObj = body.get("images");
            if (imagesObj instanceof List) {
                List<?> images = (List<?>) imagesObj;
                if (images.size() > 2) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Maximum 2 images allowed per review"));
                }
                List<String> imageUrls = new ArrayList<>();
                for (Object img : images) {
                    if (img != null && !img.toString().isEmpty()) imageUrls.add(img.toString());
                }
                review.setImages(imageUrls.isEmpty() ? null : imageUrls.toString());
            }
        }
        reviewRepository.save(review);
        return ResponseEntity.ok(Map.of("success", true, "message", "Review updated"));
    }

    // ── Delete Review ────────────────────────────────────────
    @DeleteMapping("/reviews/{reviewId}")
    public ResponseEntity<?> deleteReview(Authentication auth, @PathVariable Long reviewId) {
        User user = getUser(auth);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        Optional<Review> reviewOpt = reviewRepository.findById(reviewId);
        if (reviewOpt.isEmpty()) return ResponseEntity.notFound().build();

        Review review = reviewOpt.get();
        if (review.getUser() == null || !review.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "You can only delete your own reviews"));
        }

        reviewRepository.deleteById(reviewId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Review deleted"));
    }
}

package com.travelvista.config;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleMalformedRequest(Exception ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid request body"));
    }
    @ExceptionHandler(com.travelvista.service.AuthFailure.class)
    public ResponseEntity<Map<String, String>> handleAuth(com.travelvista.service.AuthFailure ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException ex) {
        String detail = ex.getMostSpecificCause().getMessage();
        if (detail != null && (detail.contains("uq_users_email_normalized") || detail.contains("users_email_key") || detail.contains("Key (email)=")))
            return ResponseEntity.badRequest().body(Map.of("error", "Email is already registered"));
        if (detail != null && detail.contains("uq_users_phone_normalized"))
            return ResponseEntity.badRequest().body(Map.of("error", "Phone number is already registered"));
        String message = "Cannot complete this operation — it would violate database constraints. Please remove related records first.";
        if (ex.getMessage() != null && ex.getMessage().contains("foreign key constraint")) {
            message = "Cannot delete — this record is still referenced by other items. Remove those references first.";
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneral(Exception ex) {
        String msg = "An unexpected error occurred. Please try again.";
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", msg));
    }
}

package com.travelvista.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "otp_verifications")
public class OtpVerification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(nullable = false, length = 10)
    private String code;

    /** Optional server-side transaction binding for authentication flows. */
    @Column(name = "transaction_id", length = 64)
    private String transactionId;

    /** Salted BCrypt hash for new OTPs; legacy SHA-256 auth rows require a new challenge. */
    @Column(name = "code_hash", length = 64)
    private String codeHash;

    @Column(nullable = false, length = 50)
    private String purpose; // "edit_enquiry", "delete_enquiry", "edit_lead", "delete_lead"

    @Column(name = "record_id", nullable = false)
    private Long recordId;

    @Column(name = "record_type", nullable = false, length = 20)
    private String recordType; // "enquiry" or "lead"

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private Boolean verified = false;

    @Column(name = "attempts", nullable = false)
    private Integer attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts = 5;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public OtpVerification() {}

    public OtpVerification(String email, String code, String purpose, Long recordId, String recordType, LocalDateTime expiresAt) {
        this.email = email;
        this.code = code;
        this.purpose = purpose;
        this.recordId = recordId;
        this.recordType = recordType;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getCodeHash() { return codeHash; }
    public void setCodeHash(String codeHash) { this.codeHash = codeHash; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public String getRecordType() { return recordType; }
    public void setRecordType(String recordType) { this.recordType = recordType; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public Boolean getVerified() { return verified; }
    public void setVerified(Boolean verified) { this.verified = verified; }
    public Integer getAttempts() { return attempts; }
    public void setAttempts(Integer attempts) { this.attempts = attempts; }
    public Integer getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(Integer maxAttempts) { this.maxAttempts = maxAttempts; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public boolean isExpired() {
        return !LocalDateTime.now().isBefore(this.expiresAt);
    }

    public boolean isMaxAttemptsExceeded() {
        return this.attempts >= this.maxAttempts;
    }
}

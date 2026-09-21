package com.travelvista.repository;

import com.travelvista.model.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.query.Param;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {

    Optional<OtpVerification> findTopByEmailAndPurposeAndRecordTypeAndRecordIdOrderByCreatedAtDesc(
            String email, String purpose, String recordType, Long recordId);

    List<OtpVerification> findByEmailAndPurposeAndRecordTypeAndRecordIdAndVerifiedFalse(
            String email, String purpose, String recordType, Long recordId);

    @Modifying
    @Transactional
    @Query("DELETE FROM OtpVerification o WHERE o.expiresAt < :now")
    void deleteExpired(LocalDateTime now);

    @Query("SELECT COUNT(o) FROM OtpVerification o WHERE o.email = :email AND o.createdAt > :since")
    long countRecentByEmail(String email, LocalDateTime since);

    Optional<OtpVerification> findTopByEmailAndPurposeAndRecordTypeOrderByCreatedAtDesc(
            String email, String purpose, String recordType);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<OtpVerification> findTopByEmailAndPurposeAndRecordTypeAndTransactionIdOrderByCreatedAtDesc(
            String email, String purpose, String recordType, String transactionId);

    @Query("SELECT COUNT(o) FROM OtpVerification o WHERE o.email = :email AND o.purpose = :purpose AND o.createdAt > :since")
    long countRecentByEmailAndPurpose(@Param("email") String email, @Param("purpose") String purpose, LocalDateTime since);

    @Modifying
    @Transactional
    @Query("DELETE FROM OtpVerification o WHERE o.email = :email AND o.purpose = :purpose AND o.verified = false")
    void deleteByEmailAndPurpose(@Param("email") String email, @Param("purpose") String purpose);

    List<OtpVerification> findByEmailAndPurposeAndVerifiedFalse(String email, String purpose);
}

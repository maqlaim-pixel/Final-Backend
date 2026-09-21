package com.travelvista.repository;

import com.travelvista.model.PendingRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;

public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PendingRegistration> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PendingRegistration> findByEmailAndTransactionId(String email, String transactionId);

    @Modifying
    @Query("delete from PendingRegistration p where p.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}

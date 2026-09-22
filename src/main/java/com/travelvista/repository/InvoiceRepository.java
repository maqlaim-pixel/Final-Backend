package com.travelvista.repository;

import com.travelvista.model.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"booking", "user", "items"})
    Optional<Invoice> findDetailedById(Long id);

    Optional<Invoice> findByBookingId(Long bookingId);

    List<Invoice> findByStatusOrderByCreatedAtDesc(String status);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"booking", "user", "items"})
    @Query("SELECT i FROM Invoice i ORDER BY i.createdAt DESC")
    List<Invoice> findAllLatest();

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"booking", "user", "items"})
    List<Invoice> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.status = :status")
    long countByStatus(String status);

    @Query("SELECT COALESCE(SUM(i.grandTotal), 0) FROM Invoice i WHERE i.status = 'paid'")
    java.math.BigDecimal totalPaidRevenue();

    @Query("SELECT COALESCE(SUM(i.totalTax), 0) FROM Invoice i WHERE i.status = 'paid'")
    java.math.BigDecimal totalTaxCollected();

    @Query("SELECT COALESCE(SUM(i.igstAmount), 0) FROM Invoice i WHERE i.status = 'paid'")
    java.math.BigDecimal totalIgstCollected();

    @Query("SELECT COALESCE(SUM(i.cgstAmount + i.sgstAmount), 0) FROM Invoice i WHERE i.status = 'paid'")
    java.math.BigDecimal totalCgstSgstCollected();

    boolean existsByInvoiceNumber(String invoiceNumber);
}

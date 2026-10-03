package com.travelvista.repository;

import com.travelvista.model.ContactEnquiry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.Optional;
import java.util.List;

public interface ContactEnquiryRepository extends JpaRepository<ContactEnquiry, Long> {
    @EntityGraph(attributePaths = "user")
    List<ContactEnquiry> findAllByOrderByCreatedAtDesc();
    @EntityGraph(attributePaths = "user")
    List<ContactEnquiry> findByStatusIgnoreCaseOrderByCreatedAtDesc(String status);
    @EntityGraph(attributePaths = "user")
    Optional<ContactEnquiry> findWithUserById(Long id);
    long countByStatusIgnoreCase(String status);
}

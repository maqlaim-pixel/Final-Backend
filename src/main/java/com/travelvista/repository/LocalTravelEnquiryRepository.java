package com.travelvista.repository;

import com.travelvista.model.LocalTravelEnquiry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.Optional;
import java.util.List;

public interface LocalTravelEnquiryRepository extends JpaRepository<LocalTravelEnquiry, Long> {
    @EntityGraph(attributePaths = "user")
    List<LocalTravelEnquiry> findAllByOrderByCreatedAtDesc();
    @EntityGraph(attributePaths = "user")
    List<LocalTravelEnquiry> findByStatusIgnoreCaseOrderByCreatedAtDesc(String status);
    @EntityGraph(attributePaths = "user")
    Optional<LocalTravelEnquiry> findWithUserById(Long id);
    long countByStatusIgnoreCase(String status);
}

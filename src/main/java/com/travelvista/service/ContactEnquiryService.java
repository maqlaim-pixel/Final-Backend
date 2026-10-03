package com.travelvista.service;

import com.travelvista.dto.ContactEnquiryRequest;
import com.travelvista.model.ContactEnquiry;
import com.travelvista.model.User;
import com.travelvista.repository.ContactEnquiryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class ContactEnquiryService {
    private static final Set<String> STATUSES = Set.of("NEW", "READ", "CONTACTED", "RESOLVED", "CLOSED");
    private final ContactEnquiryRepository repository;

    public ContactEnquiryService(ContactEnquiryRepository repository) { this.repository = repository; }

    @Transactional
    public ContactEnquiry create(ContactEnquiryRequest request, User user) {
        ContactEnquiry enquiry = new ContactEnquiry();
        enquiry.setUser(user);
        enquiry.setName(request.getName().trim());
        enquiry.setEmail(request.getEmail().trim().toLowerCase());
        enquiry.setPhone(request.getPhone().trim());
        enquiry.setSubject(request.getSubject().trim());
        enquiry.setMessage(request.getMessage().trim());
        return repository.save(enquiry);
    }

    @Transactional(readOnly = true)
    public List<ContactEnquiry> list(String status) {
        return status == null || status.isBlank()
                ? repository.findAllByOrderByCreatedAtDesc()
                : repository.findByStatusIgnoreCaseOrderByCreatedAtDesc(status);
    }

    @Transactional(readOnly = true)
    public ContactEnquiry get(Long id) {
        return repository.findWithUserById(id).orElseThrow(() -> new RecordNotFoundException("Contact enquiry not found"));
    }

    @Transactional
    public ContactEnquiry update(Long id, String status) {
        if (status == null || !STATUSES.contains(status.toUpperCase())) throw new EnquiryValidationException("Invalid Contact enquiry status");
        ContactEnquiry enquiry = get(id);
        enquiry.setStatus(status.toUpperCase());
        enquiry.setUpdatedAt(LocalDateTime.now());
        return repository.save(enquiry);
    }

    @Transactional(readOnly = true)
    public long countAll() { return repository.count(); }

    @Transactional(readOnly = true)
    public long countNew() { return repository.countByStatusIgnoreCase("NEW"); }
}

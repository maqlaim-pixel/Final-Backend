package com.travelvista.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelvista.dto.LocalTravelEnquiryRequest;
import com.travelvista.model.LocalTravelEnquiry;
import com.travelvista.model.User;
import com.travelvista.repository.LocalTravelEnquiryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDate;

@Service
public class LocalTravelEnquiryService {
    private static final Set<String> STATUSES = Set.of("NEW", "CONTACTED", "IN_PROGRESS", "CONFIRMED", "COMPLETED", "CANCELLED");
    private final LocalTravelEnquiryRepository repository;
    private final ObjectMapper objectMapper;

    public LocalTravelEnquiryService(LocalTravelEnquiryRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public LocalTravelEnquiry create(LocalTravelEnquiryRequest request, User user) {
        if (request.getFormData() == null || request.getFormData().isEmpty()) throw new EnquiryValidationException("Form details are required");
        validateForm(request);
        Map<String, String> fields = new LinkedHashMap<>(request.getFormData());
        LocalTravelEnquiry enquiry = new LocalTravelEnquiry();
        enquiry.setUser(user);
        enquiry.setServiceType(request.getServiceType());
        enquiry.setFormData(writeJson(fields));
        enquiry.setSourceUrl(request.getSourceUrl());
        return repository.save(enquiry);
    }

    @Transactional(readOnly = true)
    public List<LocalTravelEnquiry> list(String status) {
        return status == null || status.isBlank()
                ? repository.findAllByOrderByCreatedAtDesc()
                : repository.findByStatusIgnoreCaseOrderByCreatedAtDesc(status);
    }

    @Transactional(readOnly = true)
    public LocalTravelEnquiry get(Long id) {
        return repository.findWithUserById(id).orElseThrow(() -> new RecordNotFoundException("Local Travel enquiry not found"));
    }

    @Transactional
    public LocalTravelEnquiry update(Long id, String status, String notes) {
        if (status != null && !STATUSES.contains(status.toUpperCase())) throw new EnquiryValidationException("Invalid Local Travel enquiry status");
        if (notes != null && notes.length() > 10000) throw new EnquiryValidationException("Admin notes must be at most 10000 characters");
        LocalTravelEnquiry enquiry = get(id);
        if (status != null) enquiry.setStatus(status.toUpperCase());
        if (notes != null) enquiry.setAdminNotes(notes);
        enquiry.setUpdatedAt(LocalDateTime.now());
        return repository.save(enquiry);
    }

    @Transactional(readOnly = true)
    public long countAll() { return repository.count(); }

    @Transactional(readOnly = true)
    public long countNew() { return repository.countByStatusIgnoreCase("NEW"); }

    public Map<String, String> readFields(String json) {
        try { return objectMapper.readValue(json, new TypeReference<>() {}); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Stored Local Travel details are invalid", ex); }
    }

    private void validateForm(LocalTravelEnquiryRequest request) {
        Map<String, String> fields = request.getFormData();
        Set<String> required = switch (request.getServiceType()) {
            case "airport-transfer" -> Set.of("transferType", "airport", "pickupDate", "pickupTime", "passengers", "vehicleType");
            case "railway-station-transfer" -> Set.of("transferType", "station", "pickupDate", "pickupTime", "passengers", "vehicleType");
            case "full-day-city-tour", "half-day-city-tour" -> Set.of("city", "tourDate", "passengers", "vehicleType");
            case "outstation-cab" -> Set.of("pickupCity", "dropCity", "journeyType", "travelDate", "passengers", "vehicleType");
            case "car-rental" -> Set.of("pickupCity", "pickupDate", "pickupTime", "dropDate", "dropTime", "vehicleType");
            case "local-taxi-cab" -> Set.of("serviceType", "pickupLocation", "pickupDate", "pickupTime", "passengers", "vehicleType");
            case "corporate-transportation" -> Set.of("travelType", "pickupLocation", "dropLocation", "travelDate", "passengers");
            default -> throw new EnquiryValidationException("Unsupported Local Travel service type");
        };
        for (String key : required) {
            if (fields.get(key) == null || fields.get(key).isBlank()) throw new EnquiryValidationException(key + " is required");
        }
        for (String key : Set.of("pickupDate", "tourDate", "travelDate", "dropDate", "returnDate")) {
            String value = fields.get(key);
            if (value == null || value.isBlank()) continue;
            try {
                if (LocalDate.parse(value).isBefore(LocalDate.now())) throw new EnquiryValidationException(key + " cannot be in the past");
            } catch (java.time.format.DateTimeParseException ex) {
                throw new EnquiryValidationException(key + " must be a valid date");
            }
        }
        String passengers = fields.get("passengers");
        if (passengers != null && !passengers.isBlank()) {
            try {
                int count = Integer.parseInt(passengers);
                int maximum = "corporate-transportation".equals(request.getServiceType()) ? 45 : 12;
                if (count < 1 || count > maximum) throw new EnquiryValidationException("Passenger count is out of range");
            } catch (NumberFormatException ex) {
                throw new EnquiryValidationException("Passenger count must be a number");
            }
        }
        if ("outstation-cab".equals(request.getServiceType()) && "Round Trip".equals(fields.get("journeyType"))) {
            String returnDate = fields.get("returnDate");
            if (returnDate == null || returnDate.isBlank() || returnDate.compareTo(fields.get("travelDate")) <= 0)
                throw new EnquiryValidationException("Return date must be after the travel date");
        }
        if ("car-rental".equals(request.getServiceType())) {
            if (fields.get("dropDate").compareTo(fields.get("pickupDate")) < 0)
                throw new EnquiryValidationException("Drop date cannot be before pickup date");
            if (fields.get("dropDate").equals(fields.get("pickupDate")) && fields.get("dropTime").compareTo(fields.get("pickupTime")) <= 0)
                throw new EnquiryValidationException("Drop time must be later than pickup time");
        }
        if ("corporate-transportation".equals(request.getServiceType())
                && fields.get("pickupLocation").trim().equalsIgnoreCase(fields.get("dropLocation").trim()))
            throw new EnquiryValidationException("Pickup and drop locations cannot be identical");
        if ("local-taxi-cab".equals(request.getServiceType()) && "Hourly Rental".equals(fields.get("serviceType"))
                && (fields.get("rentalPackage") == null || fields.get("rentalPackage").isBlank()))
            throw new EnquiryValidationException("Rental package is required for hourly rentals");
        if ("local-taxi-cab".equals(request.getServiceType()) && "Sightseeing Tour".equals(fields.get("serviceType"))
                && (fields.get("destination") == null || fields.get("destination").isBlank()))
            throw new EnquiryValidationException("Destination is required for sightseeing tours");
    }

    private String writeJson(Map<String, String> value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Unable to store Local Travel details", ex); }
    }
}

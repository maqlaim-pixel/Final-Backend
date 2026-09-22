package com.travelvista.service;

import com.travelvista.model.*;
import com.travelvista.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepo;
    private final InvoiceItemRepository invoiceItemRepo;
    private final BookingRepository bookingRepo;
    private final UserRepository userRepo;
    private final SiteSettingRepository settingsRepo;
    private final EmailOtpService emailService;
    private final InvoicePdfService invoicePdfService;

    private static final BigDecimal GST_RATE = new BigDecimal("18.00");

    public InvoiceService(InvoiceRepository invoiceRepo,
                          InvoiceItemRepository invoiceItemRepo,
                          BookingRepository bookingRepo,
                          UserRepository userRepo,
                          SiteSettingRepository settingsRepo,
                          EmailOtpService emailService,
                          InvoicePdfService invoicePdfService) {
        this.invoiceRepo = invoiceRepo;
        this.invoiceItemRepo = invoiceItemRepo;
        this.bookingRepo = bookingRepo;
        this.userRepo = userRepo;
        this.settingsRepo = settingsRepo;
        this.emailService = emailService;
        this.invoicePdfService = invoicePdfService;
    }

    // ════════════════════════════════════════════════════════════════
    // 1. CREATE INVOICE (Manual — admin creates for any user)
    // ════════════════════════════════════════════════════════════════
    @Transactional
    public Invoice createInvoice(Invoice invoice, List<InvoiceItem> items,
                                 Long userId, String customerGstin, String customerState,
                                 String createdByName, String createdByEmail) {
        if (userId == null) throw new IllegalArgumentException("Customer is required");
        validateItems(items);

        // Set user
        if (userId != null) {
            User user = userRepo.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            invoice.setUser(user);
            if (invoice.getCustomerName() == null) invoice.setCustomerName(user.getName());
            if (invoice.getCustomerEmail() == null) invoice.setCustomerEmail(user.getEmail());
            if (invoice.getCustomerPhone() == null) invoice.setCustomerPhone(user.getPhone());
        }

        // Company details from settings
        invoice.setCompanyName(getSetting("company_name", "TravelVista"));
        invoice.setCompanyAddress(getSetting("company_address", "Mumbai, Maharashtra, India"));
        invoice.setCompanyGstin(getSetting("company_gstin", "27AABCT1234F1Z5"));
        invoice.setCompanyState(getSetting("company_state", "Maharashtra"));

        // Customer GST details
        invoice.setCustomerGstin(customerGstin);
        invoice.setCustomerState(customerState);

        // Invoice number
        invoice.setInvoiceNumber(generateInvoiceNumber());
        invoice.setStatus("draft");
        invoice.setInvoiceDate(LocalDate.now());
        if (invoice.getDueDate() == null) invoice.setDueDate(LocalDate.now().plusDays(30));

        // Created by
        invoice.setCreatedByName(createdByName);
        invoice.setCreatedByEmail(createdByEmail);

        // Calculate GST on each item
        boolean isIntraState = isIntraState(invoice.getCompanyState(), customerState);
        for (int i = 0; i < items.size(); i++) {
            InvoiceItem item = items.get(i);
            item.setSerialNo(i + 1);
            item.setGstRate(GST_RATE);
            item.calculateAmounts(isIntraState);
            item.setInvoice(invoice);
        }
        invoice.setItems(items);

        // Recalculate totals from items
        recalculateTotals(invoice, isIntraState);

        invoice.setCreatedAt(LocalDateTime.now());
        invoice.setUpdatedAt(LocalDateTime.now());

        return invoiceRepo.save(invoice);
    }

    // ════════════════════════════════════════════════════════════════
    // 2. UPDATE INVOICE
    // ════════════════════════════════════════════════════════════════
    @Transactional
    public Invoice updateInvoice(Long id, Invoice updates, List<InvoiceItem> items,
                                 Long userId, String customerGstin, String customerState) {
        Invoice invoice = invoiceRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        // Update basic fields
        if (updates.getCustomerName() != null) invoice.setCustomerName(updates.getCustomerName());
        if (updates.getCustomerEmail() != null) invoice.setCustomerEmail(updates.getCustomerEmail());
        if (updates.getCustomerPhone() != null) invoice.setCustomerPhone(updates.getCustomerPhone());
        if (updates.getCustomerAddress() != null) invoice.setCustomerAddress(updates.getCustomerAddress());
        if (updates.getPackageTitle() != null) invoice.setPackageTitle(updates.getPackageTitle());
        if (updates.getNotes() != null) invoice.setNotes(updates.getNotes());
        if (updates.getDueDate() != null) invoice.setDueDate(updates.getDueDate());
        if (updates.getTravelDate() != null) invoice.setTravelDate(updates.getTravelDate());
        if (updates.getEndDate() != null) invoice.setEndDate(updates.getEndDate());
        if (updates.getTravelers() != null) invoice.setTravelers(updates.getTravelers());

        // Update user
        if (userId != null) {
            User user = userRepo.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            invoice.setUser(user);
        }

        // Update GST details
        invoice.setCustomerGstin(customerGstin);
        invoice.setCustomerState(customerState);

        // Recalculate items
        boolean isIntraState = isIntraState(invoice.getCompanyState(), customerState);
        if (items != null) {
            validateItems(items);
            // Remove old items
            invoice.getItems().clear();
            for (int i = 0; i < items.size(); i++) {
                InvoiceItem item = items.get(i);
                item.setSerialNo(i + 1);
                item.setGstRate(GST_RATE);
                item.calculateAmounts(isIntraState);
                item.setInvoice(invoice);
            }
            invoice.setItems(items);
        }

        recalculateTotals(invoice, isIntraState);
        invoice.setUpdatedAt(LocalDateTime.now());

        return invoiceRepo.save(invoice);
    }

    // ════════════════════════════════════════════════════════════════
    // 3. DELETE INVOICE
    // ════════════════════════════════════════════════════════════════
    @Transactional
    public void deleteInvoice(Long id) {
        Invoice invoice = invoiceRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        invoiceRepo.delete(invoice);
    }

    // ════════════════════════════════════════════════════════════════
    // 4. GENERATE FROM BOOKING (auto-generate)
    // ════════════════════════════════════════════════════════════════
    @Transactional
    public Invoice generateFromBooking(Long bookingId, String customerGstin, String customerState) {
        Booking booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        Optional<Invoice> existing = invoiceRepo.findByBookingId(bookingId);
        if (existing.isPresent()) return existing.get();

        Invoice invoice = new Invoice();
        invoice.setBooking(booking);
        invoice.setUser(booking.getUser());
        invoice.setCustomerName(booking.getUser().getName());
        invoice.setCustomerEmail(booking.getUser().getEmail());
        invoice.setCustomerPhone(booking.getUser().getPhone());
        invoice.setCustomerGstin(customerGstin);
        invoice.setCustomerState(customerState);
        invoice.setPackageTitle(booking.getPackageTitle());
        invoice.setTravelDate(booking.getTravelDate());
        invoice.setEndDate(booking.getEndDate());
        invoice.setTravelers(booking.getTravelers());

        // Create single item for the package
        InvoiceItem item = new InvoiceItem();
        item.setSerialNo(1);
        item.setDescription("Tour Package: " + booking.getPackageTitle());
        item.setHsnCode("9954");
        item.setQuantity(booking.getTravelers() != null ? booking.getTravelers() : 1);
        BigDecimal perPerson = booking.getTotalAmount() != null
                ? booking.getTotalAmount().divide(BigDecimal.valueOf(item.getQuantity()), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        item.setRate(perPerson);
        item.setGstRate(GST_RATE);

        List<InvoiceItem> items = new ArrayList<>();
        items.add(item);

        return createInvoice(invoice, items, booking.getUser().getId(), customerGstin, customerState, "System", "system@travelvista.com");
    }

    // ════════════════════════════════════════════════════════════════
    // 5. SEND INVOICE (email / whatsapp / both)
    // ════════════════════════════════════════════════════════════════
    @Transactional
    public Invoice sendInvoice(Long id, String sendVia, String recipientEmail,
                               String recipientPhone, boolean sendAdminCopy) {
        Invoice invoice = invoiceRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        if (!"email".equalsIgnoreCase(sendVia)) {
            throw new AuthFailure(400, "Only email invoice delivery is currently supported");
        }
        String destination = invoice.getCustomerEmail();
        if (destination == null || destination.isBlank()) throw new AuthFailure(400, "Invoice recipient email is required");
        if (recipientEmail != null && !recipientEmail.isBlank() && !destination.equalsIgnoreCase(recipientEmail.trim())) {
            throw new AuthFailure(400, "Recipient email must match the invoice customer email");
        }

        byte[] pdf = invoicePdfService.generate(invoice);
        String messageId = emailService.sendInvoiceEmail(destination, invoice, pdf);

        // Persist sent metadata only after Resend accepts the request.
        invoice.setSentVia("email");
        invoice.setSentToEmail(destination);
        invoice.setSentAt(LocalDateTime.now());
        invoice.setAdminCopySent(sendAdminCopy);
        invoice.setAdminEmail(getSetting("admin_email", "admin@travelvista.com"));
        invoice.setEmailStatus("accepted");
        invoice.setEmailSentAt(LocalDateTime.now());
        if ("draft".equals(invoice.getStatus())) invoice.setStatus("sent");
        invoice.setUpdatedAt(LocalDateTime.now());
        return invoiceRepo.save(invoice);
    }

    // ════════════════════════════════════════════════════════════════
    // 6. UPDATE STATUS
    // ════════════════════════════════════════════════════════════════
    @Transactional
    public Invoice updateStatus(Long id, String status, String paymentMode, String paymentReference) {
        Invoice invoice = invoiceRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        invoice.setStatus(status);
        if (paymentMode != null) invoice.setPaymentMode(paymentMode);
        if (paymentReference != null) invoice.setPaymentReference(paymentReference);
        invoice.setUpdatedAt(LocalDateTime.now());
        return invoiceRepo.save(invoice);
    }

    // ════════════════════════════════════════════════════════════════
    // 7. GETTERS
    // ════════════════════════════════════════════════════════════════
    public Optional<Invoice> getById(Long id) {
        return invoiceRepo.findById(id);
    }

    public List<Invoice> getAll() {
        return invoiceRepo.findAllLatest();
    }

    public List<Invoice> getByUser(Long userId) {
        return invoiceRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<Invoice> getByStatus(String status) {
        return invoiceRepo.findByStatusOrderByCreatedAtDesc(status);
    }

    // ════════════════════════════════════════════════════════════════
    // 8. STATS
    // ════════════════════════════════════════════════════════════════
    public long totalInvoices() { return invoiceRepo.count(); }
    public long paidCount() { return invoiceRepo.countByStatus("paid"); }
    public long pendingCount() { return invoiceRepo.countByStatus("draft") + invoiceRepo.countByStatus("sent"); }
    public BigDecimal totalRevenue() { return invoiceRepo.totalPaidRevenue(); }
    public BigDecimal totalTaxCollected() { return invoiceRepo.totalTaxCollected(); }
    public BigDecimal totalIgst() { return invoiceRepo.totalIgstCollected(); }
    public BigDecimal totalCgstSgst() { return invoiceRepo.totalCgstSgstCollected(); }

    // ════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════
    private void recalculateTotals(Invoice invoice, boolean isIntraState) {
        BigDecimal totalBase = BigDecimal.ZERO;
        BigDecimal totalCgst = BigDecimal.ZERO;
        BigDecimal totalSgst = BigDecimal.ZERO;
        BigDecimal totalIgst = BigDecimal.ZERO;
        BigDecimal totalDisc = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal grandTotal = BigDecimal.ZERO;

        for (InvoiceItem item : invoice.getItems()) {
            totalBase = totalBase.add(item.getTaxableAmount());
            totalCgst = totalCgst.add(item.getCgstAmount());
            totalSgst = totalSgst.add(item.getSgstAmount());
            totalIgst = totalIgst.add(item.getIgstAmount());
            totalDisc = totalDisc.add(item.getDiscountAmount());
            totalTax = totalTax.add(item.getTotalTax());
            grandTotal = grandTotal.add(item.getLineTotal());
        }

        invoice.setBaseAmount(totalBase);
        invoice.setCgstAmount(totalCgst);
        invoice.setSgstAmount(totalSgst);
        invoice.setIgstAmount(totalIgst);
        invoice.setDiscountAmount(totalDisc);
        invoice.setTotalTax(totalTax);
        invoice.setTotalAmount(totalBase);
        invoice.setGrandTotal(grandTotal);

        if (isIntraState) {
            invoice.setCgstRate(new BigDecimal("9"));
            invoice.setSgstRate(new BigDecimal("9"));
            invoice.setIgstRate(BigDecimal.ZERO);
        } else {
            invoice.setIgstRate(GST_RATE);
            invoice.setCgstRate(BigDecimal.ZERO);
            invoice.setSgstRate(BigDecimal.ZERO);
        }
    }

    private void validateItems(List<InvoiceItem> items) {
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("At least one invoice item is required");
        for (InvoiceItem item : items) {
            if (item.getDescription() == null || item.getDescription().isBlank()) throw new IllegalArgumentException("Invoice item description is required");
            if (item.getQuantity() == null || item.getQuantity() <= 0) throw new IllegalArgumentException("Invoice item quantity must be positive");
            if (item.getRate() == null || item.getRate().compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("Invoice item rate cannot be negative");
            if (item.getDiscountPercent() == null || item.getDiscountPercent().compareTo(BigDecimal.ZERO) < 0 || item.getDiscountPercent().compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException("Invoice item discount must be between 0 and 100 percent");
            }
        }
    }

    private boolean isIntraState(String companyState, String customerState) {
        if (companyState == null || customerState == null) return false;
        return companyState.trim().equalsIgnoreCase(customerState.trim());
    }

    private String generateInvoiceNumber() {
        String prefix = "TV";
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        int seq = ThreadLocalRandom.current().nextInt(1000, 9999);
        String number = prefix + "-" + datePart + "-" + seq;
        while (invoiceRepo.existsByInvoiceNumber(number)) {
            seq = ThreadLocalRandom.current().nextInt(1000, 9999);
            number = prefix + "-" + datePart + "-" + seq;
        }
        return number;
    }

    private String getSetting(String key, String defaultVal) {
        return settingsRepo.findByKey(key).map(SiteSetting::getValue).orElse(defaultVal);
    }
}

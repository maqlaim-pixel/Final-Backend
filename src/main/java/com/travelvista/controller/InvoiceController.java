package com.travelvista.controller;

import com.travelvista.dto.InvoiceResponse;
import com.travelvista.model.Invoice;
import com.travelvista.model.InvoiceItem;
import com.travelvista.model.User;
import com.travelvista.repository.UserRepository;
import com.travelvista.service.InvoicePdfService;
import com.travelvista.service.AuthFailure;
import com.travelvista.service.InvoiceService;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {
    private final InvoiceService invoiceService;
    private final InvoicePdfService pdfService;
    private final UserRepository userRepository;

    public InvoiceController(InvoiceService invoiceService, InvoicePdfService pdfService, UserRepository userRepository) {
        this.invoiceService = invoiceService;
        this.pdfService = pdfService;
        this.userRepository = userRepository;
    }

    private User currentUser(Authentication auth) {
        if (auth == null) return null;
        if (auth.getPrincipal() instanceof User user) return user;
        return auth.getName() == null ? null : userRepository.findByEmail(auth.getName()).orElse(null);
    }

    private boolean staff(Authentication auth) {
        User user = currentUser(auth);
        if (user == null || user.getRole() == null) return false;
        return Set.of("admin", "super_admin", "content_manager", "editor").contains(user.getRole().getName());
    }

    private ResponseEntity<Map<String, String>> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You do not have permission to access this invoice"));
    }

    private ResponseEntity<Map<String, String>> bad(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message == null ? "Invoice request failed" : message));
    }

    @PostMapping
    public ResponseEntity<?> createInvoice(@RequestBody Map<String, Object> body, Authentication auth) {
        if (!staff(auth)) return forbidden();
        try {
            Invoice invoice = new Invoice();
            invoice.setCustomerName((String) body.get("customerName"));
            invoice.setCustomerEmail((String) body.get("customerEmail"));
            invoice.setCustomerPhone((String) body.get("customerPhone"));
            invoice.setCustomerAddress((String) body.get("customerAddress"));
            invoice.setPackageTitle((String) body.get("packageTitle"));
            invoice.setNotes((String) body.get("notes"));
            setDates(invoice, body);
            Long userId = longValue(body.get("userId"));
            List<InvoiceItem> items = parseItems(body.get("items"));
            String createdByName = Optional.ofNullable(currentUser(auth)).map(User::getName).orElse("Admin");
            String createdByEmail = Optional.ofNullable(currentUser(auth)).map(User::getEmail).orElse("admin@travelvista.com");
            return ResponseEntity.ok(InvoiceResponse.from(invoiceService.createInvoice(invoice, items, userId,
                    (String) body.get("customerGstin"), (String) body.get("customerState"), createdByName, createdByEmail)));
        } catch (Exception e) { return bad(e.getMessage()); }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateInvoice(@PathVariable Long id, @RequestBody Map<String, Object> body, Authentication auth) {
        if (!staff(auth)) return forbidden();
        try {
            Invoice updates = new Invoice();
            updates.setCustomerName((String) body.get("customerName"));
            updates.setCustomerEmail((String) body.get("customerEmail"));
            updates.setCustomerPhone((String) body.get("customerPhone"));
            updates.setCustomerAddress((String) body.get("customerAddress"));
            updates.setPackageTitle((String) body.get("packageTitle"));
            updates.setNotes((String) body.get("notes"));
            setDates(updates, body);
            return ResponseEntity.ok(InvoiceResponse.from(invoiceService.updateInvoice(id, updates, parseItems(body.get("items")),
                    longValue(body.get("userId")), (String) body.get("customerGstin"), (String) body.get("customerState"))));
        } catch (Exception e) { return bad(e.getMessage()); }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteInvoice(@PathVariable Long id, Authentication auth) {
        if (!staff(auth)) return forbidden();
        try { invoiceService.deleteInvoice(id); return ResponseEntity.ok(Map.of("message", "Invoice deleted")); }
        catch (RuntimeException e) { return bad(e.getMessage()); }
    }

    @PostMapping("/generate/{bookingId}")
    public ResponseEntity<?> generateFromBooking(@PathVariable Long bookingId,
                                                  @RequestBody(required = false) Map<String, String> body,
                                                  Authentication auth) {
        if (!staff(auth)) return forbidden();
        try {
            String gstin = body == null ? null : body.get("customerGstin");
            String state = body == null ? null : body.get("customerState");
            return ResponseEntity.ok(InvoiceResponse.from(invoiceService.generateFromBooking(bookingId, gstin, state)));
        } catch (RuntimeException e) { return bad(e.getMessage()); }
    }

    @PostMapping("/{id}/send")
    public ResponseEntity<?> sendInvoice(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body, Authentication auth) {
        if (!staff(auth)) return forbidden();
        try {
            Map<String, Object> request = body == null ? Map.of() : body;
            return ResponseEntity.ok(InvoiceResponse.from(invoiceService.sendInvoice(id, String.valueOf(request.getOrDefault("sendVia", "email")),
                    (String) request.get("recipientEmail"), (String) request.get("recipientPhone"),
                    Boolean.TRUE.equals(request.get("sendAdminCopy")))));
        } catch (Exception e) { return e instanceof AuthFailure failure
                ? ResponseEntity.status(failure.getStatus()).body(Map.of("error", failure.getMessage()))
                : bad(e.getMessage()); }
    }

    @GetMapping
    public ResponseEntity<?> getAllInvoices(Authentication auth) {
        if (!staff(auth)) return forbidden();
        return ResponseEntity.ok(invoiceService.getAll().stream().map(InvoiceResponse::from).toList());
    }

    @GetMapping("/my")
    public ResponseEntity<?> getMyInvoices(Authentication auth) {
        User user = currentUser(auth);
        if (user == null || user.getRole() == null || !"customer".equals(user.getRole().getName())) return forbidden();
        return ResponseEntity.ok(invoiceService.getByUser(user.getId()).stream().map(InvoiceResponse::from).toList());
    }

    @GetMapping("/users")
    public ResponseEntity<?> getUsers(Authentication auth) {
        if (!staff(auth)) return forbidden();
        return ResponseEntity.ok(userRepository.findAll().stream().map(u -> Map.of(
                "id", u.getId(), "name", Optional.ofNullable(u.getName()).orElse(""),
                "email", Optional.ofNullable(u.getEmail()).orElse(""), "phone", Optional.ofNullable(u.getPhone()).orElse("")
        )).toList());
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getStats(Authentication auth) {
        if (!staff(auth)) return forbidden();
        return ResponseEntity.ok(Map.of("totalInvoices", invoiceService.totalInvoices(), "paidCount", invoiceService.paidCount(),
                "pendingCount", invoiceService.pendingCount(), "totalRevenue", invoiceService.totalRevenue(),
                "totalTaxCollected", invoiceService.totalTaxCollected(), "totalIgst", invoiceService.totalIgst(),
                "totalCgstSgst", invoiceService.totalCgstSgst()));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<?> downloadPdf(@PathVariable Long id, Authentication auth) {
        try {
            Invoice invoice = invoiceService.getDetailedById(id).orElse(null);
            if (invoice == null) return ResponseEntity.notFound().build();
            User user = currentUser(auth);
            boolean admin = staff(auth);
            if (!admin && (user == null || invoice.getUser() == null || !invoice.getUser().getId().equals(user.getId()))) return forbidden();
            byte[] pdf = pdfService.generate(invoice);
            String filename = (invoice.getInvoiceNumber() == null ? "travelvista-invoice" : invoice.getInvoiceNumber()).replaceAll("[^A-Za-z0-9._-]", "-") + ".pdf";
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                    .body(pdf);
        } catch (RuntimeException e) { return bad(e.getMessage()); }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getInvoice(@PathVariable Long id, Authentication auth) {
        Invoice invoice = invoiceService.getDetailedById(id).orElse(null);
        if (invoice == null) return ResponseEntity.notFound().build();
        User user = currentUser(auth);
        if (!staff(auth) && (user == null || invoice.getUser() == null || !invoice.getUser().getId().equals(user.getId()))) return forbidden();
        return ResponseEntity.ok(InvoiceResponse.from(invoice));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body, Authentication auth) {
        if (!staff(auth)) return forbidden();
        try { return ResponseEntity.ok(InvoiceResponse.from(invoiceService.updateStatus(id, body.get("status"), body.get("paymentMode"), body.get("paymentReference")))); }
        catch (RuntimeException e) { return bad(e.getMessage()); }
    }

    private static void setDates(Invoice invoice, Map<String, Object> body) {
        if (body.get("travelDate") != null) invoice.setTravelDate(LocalDate.parse(String.valueOf(body.get("travelDate"))));
        if (body.get("endDate") != null) invoice.setEndDate(LocalDate.parse(String.valueOf(body.get("endDate"))));
        if (body.get("dueDate") != null) invoice.setDueDate(LocalDate.parse(String.valueOf(body.get("dueDate"))));
        if (body.get("travelers") != null) invoice.setTravelers(Integer.valueOf(body.get("travelers").toString()));
    }

    @SuppressWarnings("unchecked")
    private static List<InvoiceItem> parseItems(Object raw) {
        List<InvoiceItem> items = new ArrayList<>();
        if (!(raw instanceof List<?> list)) return items;
        for (Object value : list) {
            if (!(value instanceof Map<?, ?> source)) continue;
            InvoiceItem item = new InvoiceItem();
            item.setDescription((String) source.get("description"));
            item.setHsnCode(source.get("hsnCode") == null ? "9954" : String.valueOf(source.get("hsnCode")));
            item.setQuantity(source.get("quantity") == null ? 1 : Integer.valueOf(source.get("quantity").toString()));
            item.setUnit(source.get("unit") == null ? "NOS" : String.valueOf(source.get("unit")));
            item.setRate(source.get("rate") == null ? java.math.BigDecimal.ZERO : new java.math.BigDecimal(source.get("rate").toString()));
            item.setDiscountPercent(source.get("discountPercent") == null ? java.math.BigDecimal.ZERO : new java.math.BigDecimal(source.get("discountPercent").toString()));
            items.add(item);
        }
        return items;
    }

    private static Long longValue(Object value) { return value == null ? null : Long.valueOf(value.toString()); }
}

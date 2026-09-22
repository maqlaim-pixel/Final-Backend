package com.travelvista.dto;

import com.travelvista.model.Invoice;
import com.travelvista.model.InvoiceItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record InvoiceResponse(
        Long id,
        String invoiceNumber,
        Long userId,
        UserReference user,
        String customerName,
        String customerEmail,
        String customerPhone,
        String customerAddress,
        BookingReference booking,
        String companyName,
        String companyAddress,
        String companyGstin,
        String companyState,
        String customerGstin,
        String customerState,
        String packageTitle,
        LocalDate travelDate,
        LocalDate endDate,
        Integer travelers,
        BigDecimal baseAmount,
        BigDecimal cgstRate,
        BigDecimal cgstAmount,
        BigDecimal sgstRate,
        BigDecimal sgstAmount,
        BigDecimal igstRate,
        BigDecimal igstAmount,
        BigDecimal totalTax,
        BigDecimal totalAmount,
        BigDecimal discountAmount,
        BigDecimal grandTotal,
        List<Item> items,
        String status,
        String paymentMode,
        String paymentReference,
        LocalDate invoiceDate,
        LocalDate dueDate,
        String notes,
        String sentVia,
        String sentToEmail,
        String sentToPhone,
        LocalDateTime sentAt,
        Boolean adminCopySent,
        String emailStatus,
        LocalDateTime emailSentAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record UserReference(Long id, String name, String email, String phone) {}
    public record BookingReference(Long id, String bookingRef) {}
    public record Item(Long id, Integer serialNo, String description, String hsnCode, Integer quantity,
                       String unit, BigDecimal rate, BigDecimal discountPercent, BigDecimal discountAmount,
                       BigDecimal taxableAmount, BigDecimal gstRate, BigDecimal cgstRate, BigDecimal cgstAmount,
                       BigDecimal sgstRate, BigDecimal sgstAmount, BigDecimal igstRate, BigDecimal igstAmount,
                       BigDecimal totalTax, BigDecimal lineTotal) {}

    public static InvoiceResponse from(Invoice invoice) {
        String name = invoice.getCustomerName();
        String email = invoice.getCustomerEmail();
        String phone = invoice.getCustomerPhone();
        UserReference user = null;
        if (invoice.getUser() != null) {
            if (name == null) name = invoice.getUser().getName();
            if (email == null) email = invoice.getUser().getEmail();
            if (phone == null) phone = invoice.getUser().getPhone();
            user = new UserReference(invoice.getUser().getId(), invoice.getUser().getName(), invoice.getUser().getEmail(), invoice.getUser().getPhone());
        }
        BookingReference booking = invoice.getBooking() == null ? null : new BookingReference(invoice.getBooking().getId(), invoice.getBooking().getBookingRef());
        List<Item> items = invoice.getItems() == null ? List.of() : invoice.getItems().stream().map(InvoiceResponse::item).toList();
        return new InvoiceResponse(invoice.getId(), invoice.getInvoiceNumber(), invoice.getUser() == null ? null : invoice.getUser().getId(), user,
                name, email, phone, invoice.getCustomerAddress(), booking,
                invoice.getCompanyName(), invoice.getCompanyAddress(), invoice.getCompanyGstin(), invoice.getCompanyState(),
                invoice.getCustomerGstin(), invoice.getCustomerState(), invoice.getPackageTitle(), invoice.getTravelDate(), invoice.getEndDate(), invoice.getTravelers(),
                invoice.getBaseAmount(), invoice.getCgstRate(), invoice.getCgstAmount(), invoice.getSgstRate(), invoice.getSgstAmount(), invoice.getIgstRate(), invoice.getIgstAmount(),
                invoice.getTotalTax(), invoice.getTotalAmount(), invoice.getDiscountAmount(), invoice.getGrandTotal(), items, invoice.getStatus(), invoice.getPaymentMode(), invoice.getPaymentReference(),
                invoice.getInvoiceDate(), invoice.getDueDate(), invoice.getNotes(), invoice.getSentVia(), invoice.getSentToEmail(), invoice.getSentToPhone(), invoice.getSentAt(), invoice.getAdminCopySent(),
                invoice.getEmailStatus(), invoice.getEmailSentAt(), invoice.getCreatedAt(), invoice.getUpdatedAt());
    }

    private static Item item(InvoiceItem item) {
        return new Item(item.getId(), item.getSerialNo(), item.getDescription(), item.getHsnCode(), item.getQuantity(), item.getUnit(), item.getRate(), item.getDiscountPercent(), item.getDiscountAmount(), item.getTaxableAmount(), item.getGstRate(), item.getCgstRate(), item.getCgstAmount(), item.getSgstRate(), item.getSgstAmount(), item.getIgstRate(), item.getIgstAmount(), item.getTotalTax(), item.getLineTotal());
    }
}

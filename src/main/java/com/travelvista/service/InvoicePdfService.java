package com.travelvista.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.travelvista.model.Invoice;
import com.travelvista.model.InvoiceItem;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class InvoicePdfService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, new java.awt.Color(3, 105, 161));
    private static final Font SECTION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new java.awt.Color(15, 23, 42));
    private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 9, new java.awt.Color(51, 65, 85));
    private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA, 8, new java.awt.Color(100, 116, 139));

    public byte[] generate(Invoice invoice) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(new Rectangle(595, 842), 36, 36, 42, 42);
            PdfWriter.getInstance(document, output);
            document.open();

            PdfPTable header = new PdfPTable(new float[]{2.2f, 1f});
            header.setWidthPercentage(100);
            header.addCell(cell(companyBlock(invoice), new Rectangle(0, 0, 0, 0), Element.ALIGN_LEFT));
            header.addCell(cell(invoiceBlock(invoice), new Rectangle(0, 0, 0, 0), Element.ALIGN_RIGHT));
            document.add(header);
            document.add(spacer(12));

            PdfPTable bill = new PdfPTable(2);
            bill.setWidthPercentage(100);
            bill.addCell(cell(billTo(invoice), new Rectangle(0, 0, 0, 0), Element.ALIGN_LEFT));
            bill.addCell(cell(bookingBlock(invoice), new Rectangle(0, 0, 0, 0), Element.ALIGN_LEFT));
            document.add(bill);
            document.add(spacer(14));

            PdfPTable items = new PdfPTable(new float[]{0.45f, 3.4f, 0.75f, 1.1f, 1.25f, 1.3f});
            items.setWidthPercentage(100);
            String[] headers = {"#", "Description", "Qty", "Rate", "Tax", "Amount"};
            for (String headerText : headers) items.addCell(headerCell(headerText));
            for (InvoiceItem item : invoice.getItems()) {
                items.addCell(bodyCell(String.valueOf(item.getSerialNo())));
                items.addCell(bodyCell(nullToDash(item.getDescription())));
                items.addCell(bodyCell(String.valueOf(item.getQuantity())));
                items.addCell(bodyCell(money(item.getRate())));
                items.addCell(bodyCell(money(item.getTotalTax())));
                items.addCell(bodyCell(money(item.getLineTotal())));
            }
            document.add(items);
            document.add(spacer(10));

            PdfPTable totals = new PdfPTable(new float[]{3f, 1.4f});
            totals.setWidthPercentage(42);
            totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
            addTotal(totals, "Subtotal", invoice.getBaseAmount());
            addTotal(totals, "CGST", invoice.getCgstAmount());
            addTotal(totals, "SGST", invoice.getSgstAmount());
            addTotal(totals, "IGST", invoice.getIgstAmount());
            addTotal(totals, "Total Tax", invoice.getTotalTax());
            PdfPCell grandLabel = new PdfPCell(new Phrase("GRAND TOTAL", SECTION));
            grandLabel.setPadding(7);
            grandLabel.setBackgroundColor(new java.awt.Color(224, 242, 254));
            PdfPCell grandValue = new PdfPCell(new Phrase(money(invoice.getGrandTotal()), SECTION));
            grandValue.setPadding(7);
            grandValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
            grandValue.setBackgroundColor(new java.awt.Color(224, 242, 254));
            totals.addCell(grandLabel);
            totals.addCell(grandValue);
            document.add(totals);
            document.add(spacer(18));

            if (invoice.getPaymentMode() != null || invoice.getPaymentReference() != null || invoice.getNotes() != null) {
                PdfPTable footer = new PdfPTable(1);
                footer.setWidthPercentage(100);
                String payment = "Payment: " + nullToDash(invoice.getPaymentMode())
                        + (invoice.getPaymentReference() == null ? "" : " (" + invoice.getPaymentReference() + ")");
                footer.addCell(cell(new Paragraph(payment + "\nStatus: " + nullToDash(invoice.getStatus())
                        + (invoice.getNotes() == null ? "" : "\nNotes: " + invoice.getNotes()), BODY), new Rectangle(0, 0, 0, 0), Element.ALIGN_LEFT));
                document.add(footer);
            }
            document.add(spacer(18));
            Paragraph terms = new Paragraph("Thank you for choosing TravelVista.", SMALL);
            terms.setAlignment(Element.ALIGN_CENTER);
            document.add(terms);
            document.close();
            return output.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate invoice PDF", e);
        }
    }

    private Paragraph companyBlock(Invoice invoice) {
        Paragraph p = new Paragraph();
        p.add(new Phrase(nullToDash(invoice.getCompanyName()), TITLE));
        p.add(new Phrase("\n" + nullToDash(invoice.getCompanyAddress()), BODY));
        if (invoice.getCompanyGstin() != null) p.add(new Phrase("\nGSTIN: " + invoice.getCompanyGstin(), BODY));
        return p;
    }

    private Paragraph invoiceBlock(Invoice invoice) {
        Paragraph p = new Paragraph();
        p.setAlignment(Element.ALIGN_RIGHT);
        p.add(new Phrase("INVOICE\n", TITLE));
        p.add(new Phrase("No: " + nullToDash(invoice.getInvoiceNumber()) + "\n", SECTION));
        p.add(new Phrase("Date: " + format(invoice.getInvoiceDate()) + "\n", BODY));
        p.add(new Phrase("Due: " + format(invoice.getDueDate()) + "\n", BODY));
        p.add(new Phrase("Status: " + nullToDash(invoice.getStatus()), BODY));
        return p;
    }

    private Paragraph billTo(Invoice invoice) {
        Paragraph p = new Paragraph();
        p.add(new Phrase("BILL TO\n", SECTION));
        p.add(new Phrase(nullToDash(invoice.getCustomerName()) + "\n", BODY));
        p.add(new Phrase(nullToDash(invoice.getCustomerEmail()) + "\n", BODY));
        p.add(new Phrase(nullToDash(invoice.getCustomerPhone()) + "\n", BODY));
        p.add(new Phrase(nullToDash(invoice.getCustomerAddress()), BODY));
        return p;
    }

    private Paragraph bookingBlock(Invoice invoice) {
        Paragraph p = new Paragraph();
        p.add(new Phrase("TRAVEL DETAILS\n", SECTION));
        String bookingRef = invoice.getBooking() == null ? null : invoice.getBooking().getBookingRef();
        p.add(new Phrase("Booking: " + nullToDash(bookingRef) + "\n", BODY));
        p.add(new Phrase("Package: " + nullToDash(invoice.getPackageTitle()) + "\n", BODY));
        p.add(new Phrase("Travel: " + format(invoice.getTravelDate()) + " - " + format(invoice.getEndDate()) + "\n", BODY));
        p.add(new Phrase("Travelers: " + nullToDash(invoice.getTravelers()), BODY));
        return p;
    }

    private static PdfPCell headerCell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, SECTION));
        cell.setPadding(6);
        cell.setBackgroundColor(new java.awt.Color(224, 242, 254));
        return cell;
    }

    private static PdfPCell bodyCell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, BODY));
        cell.setPadding(6);
        return cell;
    }

    private static PdfPCell cell(Paragraph paragraph, Rectangle border, int alignment) {
        PdfPCell cell = new PdfPCell(paragraph);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(alignment);
        return cell;
    }

    private static void addTotal(PdfPTable table, String label, BigDecimal value) {
        table.addCell(cell(new Paragraph(label, BODY), new Rectangle(0, 0, 0, 0), Element.ALIGN_LEFT));
        PdfPCell valueCell = cell(new Paragraph(money(value), BODY), new Rectangle(0, 0, 0, 0), Element.ALIGN_RIGHT);
        table.addCell(valueCell);
    }

    private static Paragraph spacer(float height) {
        Paragraph spacer = new Paragraph(" ");
        spacer.setLeading(height);
        return spacer;
    }

    private static String money(BigDecimal amount) { return "₹ " + (amount == null ? "0.00" : amount.setScale(2, java.math.RoundingMode.HALF_UP)); }
    private static String nullToDash(Object value) { return value == null || value.toString().isBlank() ? "-" : value.toString(); }
    private static String format(LocalDate value) { return value == null ? "-" : DATE.format(value); }
}

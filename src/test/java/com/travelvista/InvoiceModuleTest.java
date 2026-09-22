package com.travelvista;

import com.travelvista.model.Invoice;
import com.travelvista.model.InvoiceItem;
import com.travelvista.service.InvoicePdfService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class InvoiceModuleTest {
    @Test
    void intraStateItemCalculatesCgstAndSgstFromAuthoritativeRate() {
        InvoiceItem item = new InvoiceItem();
        item.setDescription("Tour");
        item.setQuantity(2);
        item.setRate(new BigDecimal("1000"));
        item.setGstRate(new BigDecimal("18"));
        item.calculateAmounts(true);

        assertEquals(0, new BigDecimal("2000.00").compareTo(item.getTaxableAmount()));
        assertEquals(0, new BigDecimal("180.00").compareTo(item.getCgstAmount()));
        assertEquals(0, new BigDecimal("180.00").compareTo(item.getSgstAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(item.getIgstAmount()));
        assertEquals(0, new BigDecimal("2360.00").compareTo(item.getLineTotal()));
    }

    @Test
    void interStateItemCalculatesIgstOnly() {
        InvoiceItem item = new InvoiceItem();
        item.setDescription("Tour");
        item.setQuantity(1);
        item.setRate(new BigDecimal("1000"));
        item.setGstRate(new BigDecimal("18"));
        item.calculateAmounts(false);

        assertEquals(0, new BigDecimal("180.00").compareTo(item.getIgstAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(item.getCgstAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(item.getSgstAmount()));
    }

    @Test
    void pdfContainsInvoiceContent() {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("TV-TEST-1");
        invoice.setCustomerName("Test Customer");
        invoice.setCustomerEmail("customer@example.com");
        invoice.setCompanyName("TravelVista");
        InvoiceItem item = new InvoiceItem();
        item.setDescription("Test package");
        item.setQuantity(1);
        item.setRate(new BigDecimal("100"));
        item.setGstRate(new BigDecimal("18"));
        item.calculateAmounts(false);
        invoice.addItem(item);
        invoice.setBaseAmount(item.getTaxableAmount());
        invoice.setIgstAmount(item.getIgstAmount());
        invoice.setTotalTax(item.getTotalTax());
        invoice.setGrandTotal(item.getLineTotal());

        byte[] pdf = new InvoicePdfService().generate(invoice);
        assertTrue(pdf.length > 500);
        assertEquals('%', (char) pdf[0]);
        assertEquals('P', (char) pdf[1]);
        assertEquals('D', (char) pdf[2]);
        assertEquals('F', (char) pdf[3]);
    }
}

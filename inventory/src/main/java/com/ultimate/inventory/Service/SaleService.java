package com.ultimate.inventory.Service;

import com.ultimate.inventory.Constants.*;
import com.ultimate.inventory.DTO.SaleDTO;
import com.ultimate.inventory.DTO.SaleItemDTO;
import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.*;
import com.ultimate.inventory.Repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class SaleService {

    private static final Logger log = LoggerFactory.getLogger(SaleService.class);

    private final InvoiceRepository invoiceRepo;
    private final InvoiceDetailRepository detailRepo;
    private final ProductRepository productRepo;
    private final CustomerRepository customerRepo;
    private final InventoryService inventoryService;
    private final JournalService journalService;
    private final BusinessAccessHelper accessHelper;

    public SaleService(InvoiceRepository invoiceRepo, InvoiceDetailRepository detailRepo,
                       ProductRepository productRepo, CustomerRepository customerRepo,
                       InventoryService inventoryService, JournalService journalService,
                       BusinessAccessHelper accessHelper) {
        this.invoiceRepo = invoiceRepo;
        this.detailRepo = detailRepo;
        this.productRepo = productRepo;
        this.customerRepo = customerRepo;
        this.inventoryService = inventoryService;
        this.journalService = journalService;
        this.accessHelper = accessHelper;
    }

    @Transactional
    public Invoice createSale(Long businessId, SaleDTO dto, String userEmail) {
        accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);
        Shop shop = accessHelper.requireShopInBusiness(dto.getShopId(), businessId);

        Invoice invoice = new Invoice();
        invoice.setBusiness(business);
        invoice.setShop(shop);
        invoice.setType(InvoiceType.SALES);
        invoice.setInvoiceStatus(InvoiceStatus.COMPLETED);
        invoice.setPaymentMethod(dto.getPaymentMethod());
        invoice.setPaymentStatus(dto.getPaymentMethod() == PaymentMethod.CREDIT
                ? PaymentStatus.UNPAID : PaymentStatus.PAID);
        invoice.setNotes(dto.getNotes());
        invoice.setCreatedBy(userEmail);
        invoice.setCreatedAt(new Date());

        if (dto.getCustomerId() != null) {
            Customer customer = customerRepo.findByIdAndBusinessAndDeletedFalse(dto.getCustomerId(), business)
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Customer not found"));
            invoice.setCustomer(customer);
        }

        Invoice saved = invoiceRepo.save(invoice);
        saved.setInvoiceNumber(generateInvoiceNumber(business, InvoiceType.SALES, saved.getId()));
        invoiceRepo.save(saved);

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalCogs = BigDecimal.ZERO;
        List<InvoiceDetail> details = new ArrayList<>();

        for (SaleItemDTO item : dto.getItems()) {
            Product product = productRepo.findByIdAndBusinessAndDeletedFalse(item.getProductId(), business)
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                            "Product not found: " + item.getProductId()));

            BigDecimal unitPrice = item.getUnitPrice() != null
                    ? item.getUnitPrice()
                    : BigDecimal.valueOf(product.getSellingPrice());

            BigDecimal lineDiscount = item.getDiscountAmount() != null ? item.getDiscountAmount() : BigDecimal.ZERO;
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()))
                    .subtract(lineDiscount).setScale(2, RoundingMode.HALF_UP);

            BigDecimal costPrice = BigDecimal.valueOf(product.getCostPrice() != null ? product.getCostPrice() : 0.0);
            BigDecimal lineCogs = costPrice.multiply(BigDecimal.valueOf(item.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);

            inventoryService.deductInventory(product, shop, item.getQuantity(),
                    StockMovementType.SALE, "Sale #" + saved.getInvoiceNumber(), userEmail);

            InvoiceDetail detail = new InvoiceDetail();
            detail.setInvoice(saved);
            detail.setProduct(product);
            detail.setQuantity(item.getQuantity());
            detail.setUnitPrice(unitPrice);
            detail.setCostPrice(costPrice);
            detail.setDiscountAmount(lineDiscount);
            detail.setTotalLine(lineTotal);
            detail.setCreatedBy(userEmail);
            detail.setCreatedAt(new Date());
            details.add(detailRepo.save(detail));

            subtotal = subtotal.add(lineTotal);
            totalCogs = totalCogs.add(lineCogs);
        }

        BigDecimal invoiceDiscount = dto.getDiscountAmount() != null ? dto.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal taxableAmount = subtotal.subtract(invoiceDiscount);
        BigDecimal taxAmount = BigDecimal.ZERO;
        if (dto.getTaxRate() != null && dto.getTaxRate().compareTo(BigDecimal.ZERO) > 0) {
            taxAmount = taxableAmount.multiply(dto.getTaxRate())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        BigDecimal total = taxableAmount.add(taxAmount);

        saved.setSubtotal(subtotal);
        saved.setDiscountAmount(invoiceDiscount);
        saved.setTaxAmount(taxAmount);
        saved.setTotalAmount(total);
        saved.setAmountPaid(dto.getPaymentMethod() != PaymentMethod.CREDIT ? total : BigDecimal.ZERO);
        invoiceRepo.save(saved);

        try {
            journalService.postSaleJournal(saved, totalCogs, userEmail);
        } catch (Exception e) {
            log.warn("Journal posting skipped for sale #{}: {}", saved.getInvoiceNumber(), e.getMessage());
        }

        log.info("Sale created: invoice={} total={} by {}", saved.getInvoiceNumber(), total, userEmail);
        return saved;
    }

    public Page<Invoice> listSales(Long businessId, String userEmail, Pageable pageable) {
        accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);
        return invoiceRepo.findByBusinessAndType(business, InvoiceType.SALES, pageable);
    }

    public Invoice getSale(Long businessId, Long invoiceId, String userEmail) {
        accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);
        Invoice invoice = invoiceRepo.findByIdAndBusiness(invoiceId, business)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Invoice not found"));
        if (invoice.getType() != InvoiceType.SALES) {
            throw new AppException(HttpStatus.NOT_FOUND, "Invoice not found");
        }
        return invoice;
    }

    @Transactional
    public Invoice voidSale(Long businessId, Long invoiceId, String userEmail) {
        Invoice invoice = getSale(businessId, invoiceId, userEmail);
        if (invoice.getInvoiceStatus() == InvoiceStatus.VOID) {
            throw new AppException(HttpStatus.CONFLICT, "Invoice is already void");
        }
        if (invoice.getAmountPaid().compareTo(BigDecimal.ZERO) > 0) {
            throw new AppException(HttpStatus.CONFLICT,
                    "Cannot void an invoice with payments. Reverse the payments first.");
        }
        invoice.setInvoiceStatus(InvoiceStatus.VOID);
        invoice.setPaymentStatus(PaymentStatus.VOID);
        invoice.setUpdatedBy(userEmail);
        invoice.setUpdatedAt(new Date());
        log.info("Sale voided: invoice={} by {}", invoice.getInvoiceNumber(), userEmail);
        return invoiceRepo.save(invoice);
    }

    private String generateInvoiceNumber(Business business, InvoiceType type, Long id) {
        String prefix = type == InvoiceType.SALES ? "SALE" : "PURCH";
        return business.getCode() + "-" + prefix + "-" + String.format("%08d", id);
    }
}

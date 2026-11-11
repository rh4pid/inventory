package com.ultimate.inventory.Service;

import com.ultimate.inventory.Constants.*;
import com.ultimate.inventory.DTO.PurchaseDTO;
import com.ultimate.inventory.DTO.PurchaseItemDTO;
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
import java.util.Date;

@Service
public class PurchaseService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseService.class);

    private final InvoiceRepository invoiceRepo;
    private final InvoiceDetailRepository detailRepo;
    private final ProductRepository productRepo;
    private final SupplierRepository supplierRepo;
    private final InventoryService inventoryService;
    private final JournalService journalService;
    private final BusinessAccessHelper accessHelper;

    public PurchaseService(InvoiceRepository invoiceRepo, InvoiceDetailRepository detailRepo,
                           ProductRepository productRepo, SupplierRepository supplierRepo,
                           InventoryService inventoryService, JournalService journalService,
                           BusinessAccessHelper accessHelper) {
        this.invoiceRepo = invoiceRepo;
        this.detailRepo = detailRepo;
        this.productRepo = productRepo;
        this.supplierRepo = supplierRepo;
        this.inventoryService = inventoryService;
        this.journalService = journalService;
        this.accessHelper = accessHelper;
    }

    @Transactional
    public Invoice createPurchase(Long businessId, PurchaseDTO dto, String userEmail) {
        accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);
        Shop shop = accessHelper.requireShopInBusiness(dto.getShopId(), businessId);

        Invoice invoice = new Invoice();
        invoice.setBusiness(business);
        invoice.setShop(shop);
        invoice.setType(InvoiceType.PURCHASE);
        invoice.setInvoiceStatus(InvoiceStatus.COMPLETED);
        invoice.setPaymentMethod(dto.getPaymentMethod());
        invoice.setPaymentStatus(dto.getPaymentMethod() == PaymentMethod.CREDIT
                ? PaymentStatus.UNPAID : PaymentStatus.PAID);
        invoice.setNotes(dto.getNotes());
        invoice.setCreatedBy(userEmail);
        invoice.setCreatedAt(new Date());

        if (dto.getSupplierId() != null) {
            Supplier supplier = supplierRepo.findByIdAndBusinessAndDeletedFalse(dto.getSupplierId(), business)
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Supplier not found"));
            invoice.setSupplier(supplier);
        }

        Invoice saved = invoiceRepo.save(invoice);
        saved.setInvoiceNumber(generateInvoiceNumber(business, saved.getId()));
        invoiceRepo.save(saved);

        BigDecimal total = BigDecimal.ZERO;

        for (PurchaseItemDTO item : dto.getItems()) {
            Product product = productRepo.findByIdAndBusinessAndDeletedFalse(item.getProductId(), business)
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                            "Product not found: " + item.getProductId()));

            BigDecimal lineTotal = item.getUnitPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);

            inventoryService.addInventory(product, shop, item.getQuantity(),
                    StockMovementType.STOCK_IN, dto.getSupplierId(),
                    "Purchase #" + saved.getInvoiceNumber(), userEmail);

            InvoiceDetail detail = new InvoiceDetail();
            detail.setInvoice(saved);
            detail.setProduct(product);
            detail.setQuantity(item.getQuantity());
            detail.setUnitPrice(item.getUnitPrice());
            detail.setCostPrice(item.getUnitPrice());
            detail.setTotalLine(lineTotal);
            detail.setCreatedBy(userEmail);
            detail.setCreatedAt(new Date());
            detailRepo.save(detail);

            total = total.add(lineTotal);

            if (product.getCostPrice() == null ||
                    item.getUnitPrice().compareTo(BigDecimal.valueOf(product.getCostPrice())) != 0) {
                product.setCostPrice(item.getUnitPrice().doubleValue());
                product.setUpdatedBy(userEmail);
                product.setUpdatedAt(new Date());
                productRepo.save(product);
            }
        }

        saved.setSubtotal(total);
        saved.setTotalAmount(total);
        saved.setAmountPaid(dto.getPaymentMethod() != PaymentMethod.CREDIT ? total : BigDecimal.ZERO);
        invoiceRepo.save(saved);

        try {
            journalService.postPurchaseJournal(saved, userEmail);
        } catch (Exception e) {
            log.warn("Journal posting skipped for purchase #{}: {}", saved.getInvoiceNumber(), e.getMessage());
        }

        log.info("Purchase created: invoice={} total={} by {}", saved.getInvoiceNumber(), total, userEmail);
        return saved;
    }

    public Page<Invoice> listPurchases(Long businessId, String userEmail, Pageable pageable) {
        accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);
        return invoiceRepo.findByBusinessAndType(business, InvoiceType.PURCHASE, pageable);
    }

    public Invoice getPurchase(Long businessId, Long invoiceId, String userEmail) {
        accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);
        Invoice invoice = invoiceRepo.findByIdAndBusiness(invoiceId, business)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Invoice not found"));
        if (invoice.getType() != InvoiceType.PURCHASE) {
            throw new AppException(HttpStatus.NOT_FOUND, "Invoice not found");
        }
        return invoice;
    }

    @Transactional
    public Invoice voidPurchase(Long businessId, Long invoiceId, String userEmail) {
        Invoice invoice = getPurchase(businessId, invoiceId, userEmail);
        if (invoice.getInvoiceStatus() == InvoiceStatus.VOID) {
            throw new AppException(HttpStatus.CONFLICT, "Invoice is already void");
        }
        if (invoice.getAmountPaid().compareTo(BigDecimal.ZERO) > 0) {
            throw new AppException(HttpStatus.CONFLICT,
                    "Cannot void an invoice with payments.");
        }
        invoice.setInvoiceStatus(InvoiceStatus.VOID);
        invoice.setPaymentStatus(PaymentStatus.VOID);
        invoice.setUpdatedBy(userEmail);
        invoice.setUpdatedAt(new Date());
        log.info("Purchase voided: invoice={} by {}", invoice.getInvoiceNumber(), userEmail);
        return invoiceRepo.save(invoice);
    }

    private String generateInvoiceNumber(Business business, Long id) {
        return business.getCode() + "-PURCH-" + String.format("%08d", id);
    }
}

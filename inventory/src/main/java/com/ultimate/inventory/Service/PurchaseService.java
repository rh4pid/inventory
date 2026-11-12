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
import java.time.LocalDate;
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
    private final UnitService unitService;

    public PurchaseService(InvoiceRepository invoiceRepo, InvoiceDetailRepository detailRepo,
                           ProductRepository productRepo, SupplierRepository supplierRepo,
                           InventoryService inventoryService, JournalService journalService,
                           BusinessAccessHelper accessHelper, UnitService unitService) {
        this.unitService = unitService;
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
        BusinessUser member = accessHelper.requireCapability(businessId, userEmail, Capability.RECEIVE);
        Business business = accessHelper.requireBusiness(businessId);
        Shop shop = accessHelper.requireShopAccess(dto.getShopId(), businessId, member);

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
        boolean tracksBatches = inventoryService.tracksBatches(business);
        int lineNo = 0;

        for (PurchaseItemDTO item : dto.getItems()) {
            Product product = productRepo.findByIdAndBusinessAndDeletedFalse(item.getProductId(), business)
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                            "Product not found: " + item.getProductId()));

            BigDecimal lineTotal = item.getUnitPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);

            // Bought in a pack size (e.g. 3 Cartons @ 120): stock goes in as base units and the
            // product's cost is stored per base unit (120 / 24 per piece).
            UnitService.Conversion conv = unitService.resolve(product, item.getUnitId());
            double baseQty = conv.toBase(item.getQuantity());
            BigDecimal baseCost = item.getUnitPrice()
                    .divide(BigDecimal.valueOf(conv.factor()), 4, RoundingMode.HALF_UP);

            // With batch tracking, each line becomes a lot. Without a supplier lot number, the lot is
            // named after the purchase so it can always be traced back to this delivery.
            lineNo++;
            String batchNumber = null;
            if (tracksBatches) {
                if (item.getExpiryDate() != null && item.getExpiryDate().isBefore(LocalDate.now())) {
                    throw new AppException(HttpStatus.BAD_REQUEST, "'" + product.getProductName() +
                            "': expiry date " + item.getExpiryDate() + " is in the past");
                }
                batchNumber = item.getBatchNumber() != null && !item.getBatchNumber().isBlank()
                        ? item.getBatchNumber().trim() : saved.getInvoiceNumber() + "/" + lineNo;
            }
            inventoryService.receive(product, shop, baseQty,
                    StockMovementType.STOCK_IN, dto.getSupplierId(),
                    InventoryService.Ctx.of("Purchase #" + saved.getInvoiceNumber(), userEmail)
                            .withReference(saved.getInvoiceNumber())
                            .withEntered(InventoryService.Entered.of(item.getQuantity(), conv)),
                    BatchService.BatchInfo.of(batchNumber, item.getExpiryDate(), baseCost.doubleValue(),
                            dto.getSupplierId(), "Purchase #" + saved.getInvoiceNumber()));

            InvoiceDetail detail = new InvoiceDetail();
            detail.setInvoice(saved);
            detail.setProduct(product);
            detail.setQuantity(item.getQuantity());
            detail.setUnitName(conv.isBase() ? null : conv.productUnit().getUnit().getName());
            detail.setBaseQuantity(baseQty);
            if (batchNumber != null) {
                detail.setBatchInfo(batchNumber + (item.getExpiryDate() != null ? " (exp " + item.getExpiryDate() + ")" : ""));
            }
            detail.setUnitPrice(item.getUnitPrice());
            detail.setCostPrice(item.getUnitPrice());
            detail.setTotalLine(lineTotal);
            detail.setCreatedBy(userEmail);
            detail.setCreatedAt(new Date());
            detailRepo.save(detail);

            total = total.add(lineTotal);

            if (product.getCostPrice() == null ||
                    baseCost.compareTo(BigDecimal.valueOf(product.getCostPrice())) != 0) {
                product.setCostPrice(baseCost.doubleValue());
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
        BusinessUser member = accessHelper.requireCapability(businessId, userEmail, Capability.RECEIVE);
        Business business = accessHelper.requireBusiness(businessId);
        java.util.Set<Long> allowed = accessHelper.allowedShopIds(member);
        if (allowed != null) {
            if (allowed.isEmpty()) return Page.empty(pageable);
            return invoiceRepo.findByBusinessAndTypeAndShop_IdIn(business, InvoiceType.PURCHASE, allowed, pageable);
        }
        return invoiceRepo.findByBusinessAndType(business, InvoiceType.PURCHASE, pageable);
    }

    public Invoice getPurchase(Long businessId, Long invoiceId, String userEmail) {
        BusinessUser member = accessHelper.requireCapability(businessId, userEmail, Capability.RECEIVE);
        Business business = accessHelper.requireBusiness(businessId);
        Invoice invoice = invoiceRepo.findByIdAndBusiness(invoiceId, business)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Invoice not found"));
        if (invoice.getType() != InvoiceType.PURCHASE) {
            throw new AppException(HttpStatus.NOT_FOUND, "Invoice not found");
        }
        if (invoice.getShop() != null && !accessHelper.canUseShop(member, invoice.getShop())) {
            throw new AppException(HttpStatus.NOT_FOUND, "Invoice not found");
        }
        return invoice;
    }

    private String generateInvoiceNumber(Business business, Long id) {
        return business.getCode() + "-PURCH-" + String.format("%08d", id);
    }
}

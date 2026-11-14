package com.ultimate.inventory.Service;

import com.ultimate.inventory.Constants.*;
import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.*;
import com.ultimate.inventory.Repository.InvoiceDetailRepository;
import com.ultimate.inventory.Repository.InvoiceRepository;
import com.ultimate.inventory.Repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * What happens to a sale or purchase after it is recorded: returns (credit/debit notes),
 * refunds and voids. Stock goes back through the batches the invoice used, and every money
 * change is mirrored in the books.
 */
// Reads share one read-only transaction (one connection, one commit, cached lookups); writes declare their own.
@Transactional(readOnly = true)
@Service
public class InvoiceAdjustmentService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceAdjustmentService.class);
    private static final double EPS = 1e-9;

    private final InvoiceRepository invoiceRepo;
    private final InvoiceDetailRepository detailRepo;
    private final PaymentRepository paymentRepo;
    private final InventoryService inventoryService;
    private final JournalService journalService;
    private final BusinessAccessHelper accessHelper;

    public InvoiceAdjustmentService(InvoiceRepository invoiceRepo, InvoiceDetailRepository detailRepo,
                                    PaymentRepository paymentRepo, InventoryService inventoryService,
                                    JournalService journalService, BusinessAccessHelper accessHelper) {
        this.invoiceRepo = invoiceRepo;
        this.detailRepo = detailRepo;
        this.paymentRepo = paymentRepo;
        this.inventoryService = inventoryService;
        this.journalService = journalService;
        this.accessHelper = accessHelper;
    }

    // ── Views ────────────────────────────────────────────────────────────────

    public record LineView(Long id, Long productId, String productName, String sku, Double quantity,
                           String unitName, Double baseQuantity, Double returnedQuantity, Double returnable,
                           BigDecimal unitPrice, BigDecimal totalLine, String batchInfo) {}

    public record PaymentView(Long id, BigDecimal amount, PaymentMethod paymentMethod, String reference,
                              String notes, Date paidAt, String performedBy) {}

    public record InvoiceView(Invoice invoice, List<LineView> lines, List<PaymentView> payments,
                              BigDecimal returnedAmount, BigDecimal balanceDue) {}

    public record ReturnLine(Long detailId, Double quantity) {}

    public record ReturnRequest(List<ReturnLine> items, String reason, PaymentMethod refundMethod) {}

    public InvoiceView view(Long businessId, Long invoiceId, InvoiceType type, String userEmail) {
        Invoice invoice = requireInvoice(businessId, invoiceId, type, userEmail);
        return view(invoice);
    }

    InvoiceView view(Invoice invoice) {
        List<LineView> lines = detailRepo.findByInvoice(invoice).stream().map(d -> new LineView(
                d.getId(), d.getProduct().getId(), d.getProduct().getProductName(), d.getProduct().getSku(),
                d.getQuantity(), d.getUnitName(), d.getBaseQuantity(), nz(d.getReturnedQuantity()),
                invoice.getInvoiceStatus() == InvoiceStatus.VOID ? 0.0
                        : Math.max(0, d.getQuantity() - nz(d.getReturnedQuantity())),
                d.getUnitPrice(), d.getTotalLine(), d.getBatchInfo())).toList();
        List<PaymentView> payments = paymentRepo.findByInvoice(invoice).stream().map(p -> new PaymentView(
                p.getId(), p.getAmount(), p.getPaymentMethod(), p.getPaymentReference(), p.getNotes(),
                p.getPaidAt(), p.getPerformedBy())).toList();
        return new InvoiceView(invoice, lines, payments, returned(invoice), balanceDue(invoice));
    }

    /** What is still owed on the invoice: total less returns less payments (negative = we owe them). */
    public static BigDecimal balanceDue(Invoice invoice) {
        if (invoice.getInvoiceStatus() == InvoiceStatus.VOID) return BigDecimal.ZERO;
        return nz(invoice.getTotalAmount()).subtract(returned(invoice)).subtract(nz(invoice.getAmountPaid()));
    }

    /** Recomputes paid/partial/unpaid from the amounts. */
    public static void refreshPaymentStatus(Invoice invoice) {
        if (invoice.getInvoiceStatus() == InvoiceStatus.VOID) {
            invoice.setPaymentStatus(PaymentStatus.VOID);
            return;
        }
        BigDecimal net = nz(invoice.getTotalAmount()).subtract(returned(invoice));
        BigDecimal paid = nz(invoice.getAmountPaid());
        if (paid.compareTo(net) >= 0) invoice.setPaymentStatus(PaymentStatus.PAID);
        else if (paid.signum() > 0) invoice.setPaymentStatus(PaymentStatus.PARTIAL);
        else invoice.setPaymentStatus(PaymentStatus.UNPAID);
    }

    // ── Returns ──────────────────────────────────────────────────────────────

    /**
     * Records goods coming back from a customer (sale) or going back to a supplier (purchase).
     * Quantities are in each line's own unit. If more was paid than is now owed, the difference is
     * refunded and recorded as a negative payment. Managers only.
     */
    @Transactional
    public InvoiceView returnItems(Long businessId, Long invoiceId, InvoiceType type, ReturnRequest req,
                                   String userEmail) {
        accessHelper.requireCapability(businessId, userEmail, com.ultimate.inventory.Constants.Capability.RETURNS_AND_VOIDS);
        Invoice invoice = requireInvoice(businessId, invoiceId, type, userEmail);
        if (invoice.getInvoiceStatus() == InvoiceStatus.VOID) {
            throw new AppException(HttpStatus.CONFLICT, "This invoice is void");
        }
        if (req == null || req.items() == null || req.items().isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Choose at least one item to return");
        }
        boolean isSale = type == InvoiceType.SALES;
        Map<Long, InvoiceDetail> lines = new LinkedHashMap<>();
        detailRepo.findByInvoice(invoice).forEach(d -> lines.put(d.getId(), d));

        String ref = InventoryService.returnRef(invoice);
        String reason = req.reason() != null && !req.reason().isBlank() ? req.reason().trim() : null;
        InventoryService.Ctx ctx = new InventoryService.Ctx(
                (isSale ? "Customer return on sale #" : "Returned to supplier from purchase #")
                        + invoice.getInvoiceNumber() + (reason != null ? " | " + reason : ""),
                userEmail, null, ref, InventoryService.Entered.NONE);

        BigDecimal lineValue = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        Set<Long> seen = new HashSet<>();
        for (ReturnLine r : req.items()) {
            if (r.quantity() == null || r.quantity() <= EPS) continue;
            if (!seen.add(r.detailId())) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Each line can only be listed once");
            }
            InvoiceDetail d = lines.get(r.detailId());
            if (d == null) throw new AppException(HttpStatus.BAD_REQUEST, "Line " + r.detailId() + " is not on this invoice");
            double returnable = d.getQuantity() - nz(d.getReturnedQuantity());
            if (r.quantity() > returnable + EPS) {
                throw new AppException(HttpStatus.BAD_REQUEST, "'" + d.getProduct().getProductName() + "': only " +
                        InventoryService.fmt(returnable) + " left to return");
            }
            double perUnitBase = d.getBaseQuantity() != null && d.getQuantity() > 0
                    ? d.getBaseQuantity() / d.getQuantity() : 1.0;
            double base = r.quantity() * perUnitBase;
            InventoryService.Ctx lineCtx = ctx.withEntered(new InventoryService.Entered(r.quantity(), d.getUnitName()));
            if (isSale) inventoryService.returnFromSale(invoice, d.getProduct(), base, lineCtx);
            else inventoryService.returnToSupplier(invoice, d.getProduct(), base, lineCtx);

            BigDecimal qty = BigDecimal.valueOf(r.quantity());
            lineValue = lineValue.add(nz(d.getTotalLine()).multiply(qty)
                    .divide(BigDecimal.valueOf(d.getQuantity()), 6, RoundingMode.HALF_UP));
            cost = cost.add(nz(d.getCostPrice()).multiply(qty));
            d.setReturnedQuantity(nz(d.getReturnedQuantity()) + r.quantity());
            d.setUpdatedBy(userEmail);
            d.setUpdatedAt(new Date());
            detailRepo.save(d);
        }
        if (lineValue.signum() == 0 && cost.signum() == 0) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Enter a quantity to return");
        }

        // Invoice-level discount and tax apply to returns in proportion.
        BigDecimal total = nz(invoice.getTotalAmount());
        BigDecimal subtotal = nz(invoice.getSubtotal());
        BigDecimal value = subtotal.signum() > 0
                ? lineValue.multiply(total).divide(subtotal, 2, RoundingMode.HALF_UP)
                : lineValue.setScale(2, RoundingMode.HALF_UP);
        boolean allBack = lines.values().stream().allMatch(d -> d.getQuantity() - nz(d.getReturnedQuantity()) <= EPS);
        BigDecimal returnedBefore = returned(invoice);
        if (allBack || returnedBefore.add(value).compareTo(total) > 0) {
            value = total.subtract(returnedBefore); // last return takes exactly what is left (no rounding drift)
        }
        BigDecimal tax = total.signum() > 0
                ? nz(invoice.getTaxAmount()).multiply(value).divide(total, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        invoice.setReturnedAmount(returnedBefore.add(value));

        if (isSale) journalService.postSaleReturnJournal(invoice, value, tax, cost.setScale(2, RoundingMode.HALF_UP), ref, userEmail);
        else journalService.postPurchaseReturnJournal(invoice, value, ref, userEmail);

        // Paid more than is now owed → refund the difference.
        BigDecimal due = balanceDue(invoice);
        if (due.signum() < 0) {
            refund(invoice, due.negate(), req.refundMethod(),
                    "Refund for return " + ref + (reason != null ? " (" + reason + ")" : ""), true, userEmail);
        }
        refreshPaymentStatus(invoice);
        invoice.setUpdatedBy(userEmail);
        invoice.setUpdatedAt(new Date());
        invoiceRepo.save(invoice);
        log.info("Return on {} worth {} by {}", invoice.getInvoiceNumber(), value, userEmail);
        return view(invoice);
    }

    // ── Void ─────────────────────────────────────────────────────────────────

    /**
     * Cancels an invoice completely, paid or not: stock not already returned goes back through
     * its batches, any money paid is refunded, and every journal for the invoice is reversed.
     * Managers only.
     */
    @Transactional
    public Invoice voidInvoice(Long businessId, Long invoiceId, InvoiceType type, PaymentMethod refundMethod,
                               String userEmail) {
        accessHelper.requireCapability(businessId, userEmail, com.ultimate.inventory.Constants.Capability.RETURNS_AND_VOIDS);
        Invoice invoice = requireInvoice(businessId, invoiceId, type, userEmail);
        if (invoice.getInvoiceStatus() == InvoiceStatus.VOID) {
            throw new AppException(HttpStatus.CONFLICT, "Invoice is already void");
        }
        boolean isSale = type == InvoiceType.SALES;
        InventoryService.Ctx ctx = new InventoryService.Ctx(
                "Void of " + (isSale ? "sale" : "purchase") + " #" + invoice.getInvoiceNumber(),
                userEmail, null, InventoryService.voidRef(invoice), InventoryService.Entered.NONE);

        // Stock: whatever has not already come back through returns, per product.
        Map<Long, Product> products = new LinkedHashMap<>();
        Map<Long, Double> remaining = new LinkedHashMap<>();
        for (InvoiceDetail d : detailRepo.findByInvoice(invoice)) {
            double left = d.getQuantity() - nz(d.getReturnedQuantity());
            if (left <= EPS) continue;
            double perUnitBase = d.getBaseQuantity() != null && d.getQuantity() > 0
                    ? d.getBaseQuantity() / d.getQuantity() : 1.0;
            products.putIfAbsent(d.getProduct().getId(), d.getProduct());
            remaining.merge(d.getProduct().getId(), left * perUnitBase, Double::sum);
        }
        for (Map.Entry<Long, Double> e : remaining.entrySet()) {
            Product p = products.get(e.getKey());
            if (isSale) inventoryService.returnFromSale(invoice, p, e.getValue(), ctx);
            else inventoryService.returnToSupplier(invoice, p, e.getValue(), ctx);
        }

        // Books: reverse every journal (sale/purchase, payments, returns, refunds) first, so the
        // refund below is not double counted.
        journalService.reverseInvoiceJournals(invoice, userEmail);
        BigDecimal paid = nz(invoice.getAmountPaid());
        if (paid.signum() > 0) {
            refund(invoice, paid, refundMethod, "Refund on void", false, userEmail);
        }

        invoice.setInvoiceStatus(InvoiceStatus.VOID);
        invoice.setPaymentStatus(PaymentStatus.VOID);
        invoice.setUpdatedBy(userEmail);
        invoice.setUpdatedAt(new Date());
        log.info("{} voided: invoice={} refunded={} by {}", isSale ? "Sale" : "Purchase",
                invoice.getInvoiceNumber(), paid, userEmail);
        return invoiceRepo.save(invoice);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Records money going back as a negative payment and lowers the amount paid. */
    private void refund(Invoice invoice, BigDecimal amount, PaymentMethod method, String notes,
                        boolean postJournal, String userEmail) {
        PaymentMethod how = method != null && method != PaymentMethod.CREDIT ? method
                : invoice.getPaymentMethod() != null && invoice.getPaymentMethod() != PaymentMethod.CREDIT
                    ? invoice.getPaymentMethod() : PaymentMethod.CASH;
        Payment p = new Payment();
        p.setInvoice(invoice);
        p.setAmount(amount.negate());
        p.setPaymentMethod(how);
        p.setNotes(notes);
        p.setPaidAt(new Date());
        p.setPerformedBy(userEmail);
        p.setCreatedBy(userEmail);
        p.setCreatedAt(new Date());
        paymentRepo.save(p);
        invoice.setAmountPaid(nz(invoice.getAmountPaid()).subtract(amount));
        if (postJournal) journalService.postRefundJournal(invoice, amount, how, userEmail);
    }

    private Invoice requireInvoice(Long businessId, Long invoiceId, InvoiceType type, String userEmail) {
        // Sales are for sellers, purchases for whoever receives stock.
        BusinessUser member = accessHelper.requireCapability(businessId, userEmail,
                type == InvoiceType.SALES ? com.ultimate.inventory.Constants.Capability.SELL : com.ultimate.inventory.Constants.Capability.RECEIVE);
        Business business = accessHelper.requireBusiness(businessId);
        Invoice invoice = invoiceRepo.findByIdAndBusiness(invoiceId, business)
                .filter(i -> i.getType() == type)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Invoice not found"));
        if (invoice.getShop() != null && !accessHelper.canUseShop(member, invoice.getShop())) {
            throw new AppException(HttpStatus.NOT_FOUND, "Invoice not found");
        }
        return invoice;
    }

    static BigDecimal returned(Invoice invoice) { return nz(invoice.getReturnedAmount()); }

    private static BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    private static double nz(Double v) { return v != null ? v : 0.0; }
}

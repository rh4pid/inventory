package com.ultimate.inventory.Controller;

import com.ultimate.inventory.DTO.PaymentDTO;
import com.ultimate.inventory.DTO.PurchaseDTO;
import com.ultimate.inventory.Model.Invoice;
import com.ultimate.inventory.Model.Payment;
import com.ultimate.inventory.Constants.InvoiceType;
import com.ultimate.inventory.Constants.PaymentMethod;
import com.ultimate.inventory.Service.InvoiceAdjustmentService;
import com.ultimate.inventory.Service.PaymentService;
import com.ultimate.inventory.Service.PurchaseService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/businesses/{businessId}/purchases")
public class PurchaseController {

    private final PurchaseService purchaseService;
    private final PaymentService paymentService;
    private final InvoiceAdjustmentService adjustments;

    public PurchaseController(PurchaseService purchaseService, PaymentService paymentService, InvoiceAdjustmentService adjustments) {
        this.adjustments = adjustments;
        this.purchaseService = purchaseService;
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<?> create(@PathVariable Long businessId,
                                    @RequestBody @Valid PurchaseDTO dto,
                                    @AuthenticationPrincipal String email) {
        Invoice invoice = purchaseService.createPurchase(businessId, dto, email);
        return ResponseEntity.status(HttpStatus.CREATED).body(invoice);
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable Long businessId,
                                  @PageableDefault(size = 20) Pageable pageable,
                                  @AuthenticationPrincipal String email) {
        Page<Invoice> page = purchaseService.listPurchases(businessId, email, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{invoiceId}")
    public ResponseEntity<?> get(@PathVariable Long businessId,
                                 @PathVariable Long invoiceId,
                                 @AuthenticationPrincipal String email) {
        Invoice invoice = purchaseService.getPurchase(businessId, invoiceId, email);
        return ResponseEntity.ok(invoice);
    }

    public record VoidBody(PaymentMethod refundMethod) {}

    /** Voids the invoice, paid or not: stock goes back, payments are refunded, the books are reversed. */
    @PostMapping("/{invoiceId}/void")
    public ResponseEntity<?> voidPurchase(@PathVariable Long businessId,
                                      @PathVariable Long invoiceId,
                                      @RequestBody(required = false) VoidBody body,
                                      @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(adjustments.voidInvoice(businessId, invoiceId, InvoiceType.PURCHASE,
                body != null ? body.refundMethod() : null, email));
    }

    /** The invoice with its lines (and what is left to return), payments and balance. */
    @GetMapping("/{invoiceId}/detail")
    public ResponseEntity<?> detail(@PathVariable Long businessId, @PathVariable Long invoiceId,
                                    @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(adjustments.view(businessId, invoiceId, InvoiceType.PURCHASE, email));
    }

    @PostMapping("/{invoiceId}/returns")
    public ResponseEntity<?> returnItems(@PathVariable Long businessId, @PathVariable Long invoiceId,
                                         @RequestBody InvoiceAdjustmentService.ReturnRequest body,
                                         @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(adjustments.returnItems(businessId, invoiceId, InvoiceType.PURCHASE, body, email));
    }

    @PostMapping("/{invoiceId}/payments")
    public ResponseEntity<?> recordPayment(@PathVariable Long businessId,
                                           @PathVariable Long invoiceId,
                                           @RequestBody @Valid PaymentDTO dto,
                                           @AuthenticationPrincipal String email) {
        Payment payment = paymentService.recordPayment(businessId, invoiceId, InvoiceType.PURCHASE, dto, email);
        return ResponseEntity.status(HttpStatus.CREATED).body(payment);
    }

    @GetMapping("/{invoiceId}/payments")
    public ResponseEntity<?> listPayments(@PathVariable Long businessId,
                                          @PathVariable Long invoiceId,
                                          @AuthenticationPrincipal String email) {
        List<Payment> payments = paymentService.listPayments(businessId, invoiceId, InvoiceType.PURCHASE, email);
        return ResponseEntity.ok(payments);
    }
}

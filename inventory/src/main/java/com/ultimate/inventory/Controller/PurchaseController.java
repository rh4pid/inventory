package com.ultimate.inventory.Controller;

import com.ultimate.inventory.DTO.PaymentDTO;
import com.ultimate.inventory.DTO.PurchaseDTO;
import com.ultimate.inventory.Model.Invoice;
import com.ultimate.inventory.Model.Payment;
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

    public PurchaseController(PurchaseService purchaseService, PaymentService paymentService) {
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

    @PostMapping("/{invoiceId}/void")
    public ResponseEntity<?> voidPurchase(@PathVariable Long businessId,
                                          @PathVariable Long invoiceId,
                                          @AuthenticationPrincipal String email) {
        Invoice invoice = purchaseService.voidPurchase(businessId, invoiceId, email);
        return ResponseEntity.ok(invoice);
    }

    @PostMapping("/{invoiceId}/payments")
    public ResponseEntity<?> recordPayment(@PathVariable Long businessId,
                                           @PathVariable Long invoiceId,
                                           @RequestBody @Valid PaymentDTO dto,
                                           @AuthenticationPrincipal String email) {
        Payment payment = paymentService.recordPayment(businessId, invoiceId, dto, email);
        return ResponseEntity.status(HttpStatus.CREATED).body(payment);
    }

    @GetMapping("/{invoiceId}/payments")
    public ResponseEntity<?> listPayments(@PathVariable Long businessId,
                                          @PathVariable Long invoiceId,
                                          @AuthenticationPrincipal String email) {
        List<Payment> payments = paymentService.listPayments(businessId, invoiceId, email);
        return ResponseEntity.ok(payments);
    }
}

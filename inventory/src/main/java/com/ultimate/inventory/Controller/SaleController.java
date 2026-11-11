package com.ultimate.inventory.Controller;

import com.ultimate.inventory.DTO.PaymentDTO;
import com.ultimate.inventory.DTO.SaleDTO;
import com.ultimate.inventory.Model.Invoice;
import com.ultimate.inventory.Model.Payment;
import com.ultimate.inventory.Service.PaymentService;
import com.ultimate.inventory.Service.SaleService;
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
@RequestMapping("/businesses/{businessId}/sales")
public class SaleController {

    private final SaleService saleService;
    private final PaymentService paymentService;

    public SaleController(SaleService saleService, PaymentService paymentService) {
        this.saleService = saleService;
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<?> create(@PathVariable Long businessId,
                                    @RequestBody @Valid SaleDTO dto,
                                    @AuthenticationPrincipal String email) {
        Invoice invoice = saleService.createSale(businessId, dto, email);
        return ResponseEntity.status(HttpStatus.CREATED).body(invoice);
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable Long businessId,
                                  @PageableDefault(size = 20) Pageable pageable,
                                  @AuthenticationPrincipal String email) {
        Page<Invoice> page = saleService.listSales(businessId, email, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{invoiceId}")
    public ResponseEntity<?> get(@PathVariable Long businessId,
                                 @PathVariable Long invoiceId,
                                 @AuthenticationPrincipal String email) {
        Invoice invoice = saleService.getSale(businessId, invoiceId, email);
        return ResponseEntity.ok(invoice);
    }

    @PostMapping("/{invoiceId}/void")
    public ResponseEntity<?> voidSale(@PathVariable Long businessId,
                                      @PathVariable Long invoiceId,
                                      @AuthenticationPrincipal String email) {
        Invoice invoice = saleService.voidSale(businessId, invoiceId, email);
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

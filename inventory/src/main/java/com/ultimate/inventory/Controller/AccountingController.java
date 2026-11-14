package com.ultimate.inventory.Controller;

import com.ultimate.inventory.Constants.JournalTransactionType;
import com.ultimate.inventory.Service.AccountingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** The books: owner's money, moving money, manual entries, journals, ledger and reports (admins). */
@RestController
@RequestMapping("/businesses/{businessId}/accounting")
public class AccountingController {

    private final AccountingService accounting;

    public AccountingController(AccountingService accounting) {
        this.accounting = accounting;
    }

    @GetMapping("/summary")
    public ResponseEntity<?> summary(@PathVariable Long businessId, @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(accounting.summary(businessId, email));
    }

    @GetMapping("/money-accounts")
    public ResponseEntity<?> moneyAccounts(@PathVariable Long businessId, @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(accounting.moneyAccounts(businessId, email));
    }

    @PostMapping("/capital")
    public ResponseEntity<?> capital(@PathVariable Long businessId, @RequestBody AccountingService.OwnerMoney body,
                                     @AuthenticationPrincipal String email) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accounting.recordCapital(businessId, body, email));
    }

    @PostMapping("/drawings")
    public ResponseEntity<?> drawings(@PathVariable Long businessId, @RequestBody AccountingService.OwnerMoney body,
                                      @AuthenticationPrincipal String email) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accounting.recordDrawings(businessId, body, email));
    }

    @PostMapping("/transfers")
    public ResponseEntity<?> transfer(@PathVariable Long businessId, @RequestBody AccountingService.Transfer body,
                                      @AuthenticationPrincipal String email) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accounting.transfer(businessId, body, email));
    }

    @GetMapping("/journals")
    public ResponseEntity<?> journals(@PathVariable Long businessId,
                                      @RequestParam(required = false) JournalTransactionType type,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "25") int size,
                                      @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(accounting.journals(businessId, type, from, to, page, size, email));
    }

    @PostMapping("/journals")
    public ResponseEntity<?> manual(@PathVariable Long businessId, @RequestBody AccountingService.ManualJournal body,
                                    @AuthenticationPrincipal String email) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accounting.manualJournal(businessId, body, email));
    }

    @GetMapping("/ledger/{accountId}")
    public ResponseEntity<?> ledger(@PathVariable Long businessId, @PathVariable Long accountId,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                    @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(accounting.ledger(businessId, accountId, from, to, email));
    }

    @GetMapping("/reports/trial-balance")
    public ResponseEntity<?> trialBalance(@PathVariable Long businessId,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
                                          @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(accounting.trialBalance(businessId, asOf, email));
    }

    @GetMapping("/reports/profit-loss")
    public ResponseEntity<?> profitLoss(@PathVariable Long businessId,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                        @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(accounting.profitAndLoss(businessId, from, to, email));
    }

    @GetMapping("/reports/balance-sheet")
    public ResponseEntity<?> balanceSheet(@PathVariable Long businessId,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
                                          @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(accounting.balanceSheet(businessId, asOf, email));
    }

    /** Posts the books for sales/purchases made before the company had accounts. */
    @PostMapping("/catch-up")
    public ResponseEntity<?> catchUp(@PathVariable Long businessId, @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(accounting.catchUp(businessId, email));
    }
}

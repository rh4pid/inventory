package com.ultimate.inventory.Controller;

import com.ultimate.inventory.Service.ExpenseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Business expenses paid from petty cash (or another money account), with manager approval for cashiers. */
@RestController
@RequestMapping("/businesses/{businessId}/expenses")
public class ExpenseController {

    private final ExpenseService expenses;

    public ExpenseController(ExpenseService expenses) {
        this.expenses = expenses;
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable Long businessId,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(required = false) Long shopId,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "25") int size,
                                  @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(expenses.list(businessId, status, shopId, page, size, email));
    }

    @GetMapping("/options")
    public ResponseEntity<?> options(@PathVariable Long businessId, @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(expenses.options(businessId, email));
    }

    @GetMapping("/pending-count")
    public ResponseEntity<?> pendingCount(@PathVariable Long businessId, @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(Map.of("count", expenses.pendingCount(businessId, email)));
    }

    @PostMapping
    public ResponseEntity<?> create(@PathVariable Long businessId, @RequestBody ExpenseService.NewExpense body,
                                    @AuthenticationPrincipal String email) {
        return ResponseEntity.status(HttpStatus.CREATED).body(expenses.create(businessId, body, email));
    }

    @PostMapping("/{expenseId}/approve")
    public ResponseEntity<?> approve(@PathVariable Long businessId, @PathVariable Long expenseId,
                                     @RequestBody(required = false) ExpenseService.Decision body,
                                     @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(expenses.approve(businessId, expenseId, body, email));
    }

    @PostMapping("/{expenseId}/reject")
    public ResponseEntity<?> reject(@PathVariable Long businessId, @PathVariable Long expenseId,
                                    @RequestBody(required = false) ExpenseService.Decision body,
                                    @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(expenses.reject(businessId, expenseId, body, email));
    }

    @PostMapping("/{expenseId}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long businessId, @PathVariable Long expenseId,
                                    @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(expenses.cancel(businessId, expenseId, email));
    }

    @PostMapping("/{expenseId}/void")
    public ResponseEntity<?> voidExpense(@PathVariable Long businessId, @PathVariable Long expenseId,
                                         @RequestBody(required = false) ExpenseService.Decision body,
                                         @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(expenses.voidExpense(businessId, expenseId, body, email));
    }
}

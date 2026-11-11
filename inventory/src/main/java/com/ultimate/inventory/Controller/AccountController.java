package com.ultimate.inventory.Controller;

import com.ultimate.inventory.DTO.AccountDTO;
import com.ultimate.inventory.Model.Accounts.Account;
import com.ultimate.inventory.Service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/businesses/{businessId}/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/setup-defaults")
    public ResponseEntity<?> setupDefaults(@PathVariable Long businessId,
                                           @AuthenticationPrincipal String email) {
        List<Account> accounts = accountService.setupDefaultAccounts(businessId, email);
        return ResponseEntity.ok(accounts);
    }

    @PostMapping
    public ResponseEntity<?> create(@PathVariable Long businessId,
                                    @RequestBody @Valid AccountDTO dto,
                                    @AuthenticationPrincipal String email) {
        Account account = accountService.createAccount(businessId, dto, email);
        return ResponseEntity.status(HttpStatus.CREATED).body(account);
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable Long businessId,
                                  @AuthenticationPrincipal String email) {
        List<Account> accounts = accountService.listAccounts(businessId, email);
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<?> get(@PathVariable Long businessId,
                                 @PathVariable Long accountId,
                                 @AuthenticationPrincipal String email) {
        Account account = accountService.getAccount(businessId, accountId, email);
        return ResponseEntity.ok(account);
    }

    @PutMapping("/{accountId}")
    public ResponseEntity<?> update(@PathVariable Long businessId,
                                    @PathVariable Long accountId,
                                    @RequestBody @Valid AccountDTO dto,
                                    @AuthenticationPrincipal String email) {
        Account account = accountService.updateAccount(businessId, accountId, dto, email);
        return ResponseEntity.ok(account);
    }
}

package com.ultimate.inventory.Service;

import com.ultimate.inventory.Constants.BusinessType;
import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Repository.BusinessRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.UUID;

/**
 * Everything a new company needs before anyone uses it: the record and its code, the feature preset
 * for its business type, and the standard chart of accounts. Used by owners creating their own company
 * and by the super admin console.
 */
@Component
public class CompanySetup {

    private final BusinessRepository businessRepo;
    private final SettingsService settingsService;
    private final AccountService accountService;

    public CompanySetup(BusinessRepository businessRepo, SettingsService settingsService, AccountService accountService) {
        this.businessRepo = businessRepo;
        this.settingsService = settingsService;
        this.accountService = accountService;
    }

    @Transactional
    public Business create(String name, String email, String contact, BusinessType type, String by) {
        if (name == null || name.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Company name is required");
        }
        String bizEmail = email != null && !email.isBlank() ? UserAccounts.normalizeEmail(email) : null;
        if (bizEmail != null && businessRepo.findByBusinessEmail(bizEmail) != null) {
            throw new AppException(HttpStatus.CONFLICT, "Another company already uses " + bizEmail);
        }

        Business b = new Business();
        b.setName(name.trim());
        b.setBusinessEmail(bizEmail);
        b.setBusinessContact(contact == null || contact.isBlank() ? null : contact.trim());
        b.setSuspended(false);
        b.setLocked(false);
        b.setDeleted(false);
        b.setCreatedBy(by);
        b.setCreatedAt(new Date());
        // Code needs the id: save with a unique placeholder first (the column is NOT NULL).
        b.setCode("INIT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        b = businessRepo.save(b);
        b.setCode("BIZ" + String.format("%05d", b.getId()));
        b = businessRepo.save(b);
        settingsService.applyBusinessType(b, type != null ? type : BusinessType.GENERAL_RETAIL, by);
        accountService.ensureDefaults(b, by); // the standard chart of accounts, ready to post to
        return b;
    }
}

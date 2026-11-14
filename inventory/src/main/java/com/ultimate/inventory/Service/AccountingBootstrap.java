package com.ultimate.inventory.Service;

import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Repository.BusinessRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * On start-up, gives every company the full standard chart of accounts (companies created before
 * the chart grew, or before accounts were set up automatically, get only what they are missing).
 */
@Component
public class AccountingBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AccountingBootstrap.class);

    private final BusinessRepository businessRepo;
    private final AccountService accountService;

    public AccountingBootstrap(BusinessRepository businessRepo, AccountService accountService) {
        this.businessRepo = businessRepo;
        this.accountService = accountService;
    }

    @Override
    public void run(ApplicationArguments args) {
        int added = 0;
        for (Business b : businessRepo.findAll()) {
            try {
                added += accountService.ensureDefaults(b, "system");
            } catch (RuntimeException e) {
                log.warn("Could not set up standard accounts for business {}: {}", b.getId(), e.getMessage());
            }
        }
        if (added > 0) log.info("Standard chart of accounts: added {} accounts across companies", added);
    }
}

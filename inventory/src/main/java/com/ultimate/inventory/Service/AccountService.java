package com.ultimate.inventory.Service;

import com.ultimate.inventory.Constants.AccountPurpose;
import com.ultimate.inventory.Constants.AccountType;
import com.ultimate.inventory.DTO.AccountDTO;
import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.Accounts.Account;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Repository.Accounts.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepo;
    private final BusinessAccessHelper accessHelper;

    public AccountService(AccountRepository accountRepo, BusinessAccessHelper accessHelper) {
        this.accountRepo = accountRepo;
        this.accessHelper = accessHelper;
    }

    @Transactional
    public List<Account> setupDefaultAccounts(Long businessId, String userEmail) {
        accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);

        Object[][] defaults = {
            {"1100", "Cash in Hand",        AccountType.ASSET,     AccountPurpose.CASH},
            {"1101", "Bank Account",         AccountType.ASSET,     AccountPurpose.BANK},
            {"1200", "Accounts Receivable",  AccountType.ASSET,     AccountPurpose.ACCOUNTS_RECEIVABLE},
            {"1300", "Inventory",            AccountType.ASSET,     AccountPurpose.INVENTORY},
            {"2000", "Accounts Payable",     AccountType.LIABILITY, AccountPurpose.ACCOUNTS_PAYABLE},
            {"2100", "Tax Payable",          AccountType.LIABILITY, AccountPurpose.TAX_PAYABLE},
            {"3000", "Sales Revenue",        AccountType.REVENUE,   AccountPurpose.SALES_REVENUE},
            {"4000", "Cost of Goods Sold",   AccountType.EXPENSE,   AccountPurpose.COST_OF_GOODS_SOLD},
            {"4100", "Purchase Expense",     AccountType.EXPENSE,   AccountPurpose.PURCHASE},
            {"4200", "Discount Allowed",     AccountType.EXPENSE,   AccountPurpose.DISCOUNT_ALLOWED},
        };

        for (Object[] row : defaults) {
            String code = (String) row[0];
            if (!accountRepo.existsByBusinessAndAccountCode(business, code)) {
                Account acc = new Account();
                acc.setBusiness(business);
                acc.setAccountCode(code);
                acc.setAccountName((String) row[1]);
                acc.setAccountType((AccountType) row[2]);
                acc.setAccountPurpose((AccountPurpose) row[3]);
                acc.setActive(true);
                acc.setCreatedBy(userEmail);
                acc.setCreatedAt(new Date());
                accountRepo.save(acc);
            }
        }

        log.info("Default accounts set up for business {} by {}", businessId, userEmail);
        return accountRepo.findByBusinessAndActiveTrue(business);
    }

    @Transactional
    public Account createAccount(Long businessId, AccountDTO dto, String userEmail) {
        accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);

        if (accountRepo.existsByBusinessAndAccountCode(business, dto.getAccountCode())) {
            throw new AppException(HttpStatus.CONFLICT, "Account code already exists");
        }

        Account account = new Account();
        account.setBusiness(business);
        account.setAccountCode(dto.getAccountCode());
        account.setAccountName(dto.getAccountName());
        account.setAccountType(dto.getAccountType());
        account.setAccountPurpose(dto.getAccountPurpose());
        account.setActive(true);
        account.setCreatedBy(userEmail);
        account.setCreatedAt(new Date());
        return accountRepo.save(account);
    }

    public List<Account> listAccounts(Long businessId, String userEmail) {
        accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);
        return accountRepo.findByBusinessAndActiveTrue(business);
    }

    public Account getAccount(Long businessId, Long accountId, String userEmail) {
        accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);
        return accountRepo.findByIdAndBusiness(accountId, business)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    @Transactional
    public Account updateAccount(Long businessId, Long accountId, AccountDTO dto, String userEmail) {
        accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);

        Account account = accountRepo.findByIdAndBusiness(accountId, business)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Account not found"));

        if (!account.getAccountCode().equals(dto.getAccountCode()) &&
                accountRepo.existsByBusinessAndAccountCode(business, dto.getAccountCode())) {
            throw new AppException(HttpStatus.CONFLICT, "Account code already exists");
        }

        account.setAccountCode(dto.getAccountCode());
        account.setAccountName(dto.getAccountName());
        account.setAccountType(dto.getAccountType());
        account.setAccountPurpose(dto.getAccountPurpose());
        account.setUpdatedBy(userEmail);
        account.setUpdatedAt(new Date());
        return accountRepo.save(account);
    }

    public Account findRequiredAccount(Business business, AccountPurpose purpose) {
        return accountRepo.findByBusinessAndAccountPurpose(business, purpose)
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST,
                        "No account configured for " + purpose + ". Please set up your chart of accounts first " +
                        "(POST /businesses/{id}/accounts/setup-defaults)."));
    }
}

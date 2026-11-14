package com.ultimate.inventory.Service;

import com.ultimate.inventory.Constants.AccountPurpose;
import com.ultimate.inventory.Constants.AccountType;
import com.ultimate.inventory.Constants.Capability;
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

import java.util.*;

/**
 * The chart of accounts. Every company gets the standard accounts below automatically (when it is
 * created, and on start-up for older companies), so sales, purchases, payments, expenses and stock
 * write-offs always have somewhere to post.
 */
// Reads share one read-only transaction (one connection, one commit, cached lookups); writes declare their own.
@Transactional(readOnly = true)
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    /** One line of the standard chart. Codes: 1 assets, 2 liabilities, 3 equity, 4 income, 5–6 expenses. */
    public record Default(String code, String name, AccountType type, AccountPurpose purpose) {}

    public static final List<Default> DEFAULTS = List.of(
        new Default("1100", "Cash in Hand",                 AccountType.ASSET,     AccountPurpose.CASH),
        new Default("1101", "Bank Account",                 AccountType.ASSET,     AccountPurpose.BANK),
        new Default("1102", "Mobile Money",                 AccountType.ASSET,     AccountPurpose.MOBILE_MONEY),
        new Default("1110", "Petty Cash",                   AccountType.ASSET,     AccountPurpose.PETTY_CASH),
        new Default("1200", "Accounts Receivable",          AccountType.ASSET,     AccountPurpose.ACCOUNTS_RECEIVABLE),
        new Default("1300", "Inventory",                    AccountType.ASSET,     AccountPurpose.INVENTORY),
        new Default("2000", "Accounts Payable",             AccountType.LIABILITY, AccountPurpose.ACCOUNTS_PAYABLE),
        new Default("2100", "Tax Payable",                  AccountType.LIABILITY, AccountPurpose.TAX_PAYABLE),
        new Default("3000", "Owner's Capital",              AccountType.EQUITY,    AccountPurpose.CAPITAL),
        new Default("3100", "Owner's Drawings",             AccountType.EQUITY,    AccountPurpose.DRAWINGS),
        new Default("3200", "Retained Earnings",            AccountType.EQUITY,    AccountPurpose.RETAINED_EARNINGS),
        new Default("4000", "Sales Revenue",                AccountType.REVENUE,   AccountPurpose.SALES_REVENUE),
        new Default("4100", "Other Income",                 AccountType.REVENUE,   AccountPurpose.OTHER_INCOME),
        new Default("5000", "Cost of Goods Sold",           AccountType.EXPENSE,   AccountPurpose.COST_OF_GOODS_SOLD),
        new Default("5100", "Purchase Expense",             AccountType.EXPENSE,   AccountPurpose.PURCHASE),
        new Default("5200", "Discount Allowed",             AccountType.EXPENSE,   AccountPurpose.DISCOUNT_ALLOWED),
        new Default("5300", "Stock Losses & Adjustments",   AccountType.EXPENSE,   AccountPurpose.STOCK_ADJUSTMENT),
        new Default("6000", "Electricity",                  AccountType.EXPENSE,   AccountPurpose.EXPENSE),
        new Default("6010", "Water",                        AccountType.EXPENSE,   AccountPurpose.EXPENSE),
        new Default("6020", "Rent",                         AccountType.EXPENSE,   AccountPurpose.EXPENSE),
        new Default("6030", "Salaries & Wages",             AccountType.EXPENSE,   AccountPurpose.EXPENSE),
        new Default("6040", "Transport & Fuel",             AccountType.EXPENSE,   AccountPurpose.EXPENSE),
        new Default("6050", "Repairs & Maintenance",        AccountType.EXPENSE,   AccountPurpose.EXPENSE),
        new Default("6060", "Internet & Phone",             AccountType.EXPENSE,   AccountPurpose.EXPENSE),
        new Default("6070", "Cleaning & Sanitation",        AccountType.EXPENSE,   AccountPurpose.EXPENSE),
        new Default("6080", "Shop & Office Supplies",       AccountType.EXPENSE,   AccountPurpose.EXPENSE),
        new Default("6090", "Bank & MoMo Charges",          AccountType.EXPENSE,   AccountPurpose.EXPENSE),
        new Default("6100", "Security",                     AccountType.EXPENSE,   AccountPurpose.EXPENSE),
        new Default("6900", "Miscellaneous Expenses",       AccountType.EXPENSE,   AccountPurpose.EXPENSE)
    );

    /** The first version of the chart numbered income 3xxx and expenses 4xxx; move those to the standard codes. */
    private static final List<Object[]> LEGACY_CODES = List.of(
        new Object[]{"4200", AccountPurpose.DISCOUNT_ALLOWED, "5200"},
        new Object[]{"4100", AccountPurpose.PURCHASE, "5100"},
        new Object[]{"4000", AccountPurpose.COST_OF_GOODS_SOLD, "5000"},
        new Object[]{"3000", AccountPurpose.SALES_REVENUE, "4000"}
    );

    private final AccountRepository accountRepo;
    private final BusinessAccessHelper accessHelper;

    public AccountService(AccountRepository accountRepo, BusinessAccessHelper accessHelper) {
        this.accountRepo = accountRepo;
        this.accessHelper = accessHelper;
    }

    // ── Standard chart ───────────────────────────────────────────────────────

    /**
     * Makes sure the company has every standard account; adds only what is missing and never
     * touches accounts people created or renamed. Safe to call any number of times.
     * @return how many accounts were added
     */
    @Transactional
    public int ensureDefaults(Business business, String actor) {
        List<Account> existing = accountRepo.findByBusinessOrderByAccountCodeAsc(business);
        Set<String> codes = new HashSet<>();
        existing.forEach(a -> codes.add(a.getAccountCode()));

        // Renumber the old default codes (only accounts still on their old code and purpose).
        for (Object[] legacy : LEGACY_CODES) {
            String from = (String) legacy[0], to = (String) legacy[2];
            if (codes.contains(to)) continue;
            for (Account a : existing) {
                if (from.equals(a.getAccountCode()) && legacy[1] == a.getAccountPurpose()) {
                    a.setAccountCode(to);
                    a.setUpdatedAt(new Date());
                    a.setUpdatedBy(actor);
                    accountRepo.save(a);
                    codes.remove(from);
                    codes.add(to);
                }
            }
        }

        Set<AccountPurpose> purposes = EnumSet.noneOf(AccountPurpose.class);
        Set<String> names = new HashSet<>();
        for (Account a : existing) {
            if (a.getAccountPurpose() != null) purposes.add(a.getAccountPurpose());
            if (a.getAccountName() != null) names.add(a.getAccountName().trim().toLowerCase());
        }

        int added = 0;
        for (Default d : DEFAULTS) {
            boolean have = d.purpose().isSystem() ? purposes.contains(d.purpose())
                    : names.contains(d.name().toLowerCase());
            if (have) continue;
            String code = d.code();
            while (codes.contains(code)) code = String.valueOf(Integer.parseInt(code) + 1);
            Account acc = new Account();
            acc.setBusiness(business);
            acc.setAccountCode(code);
            acc.setAccountName(d.name());
            acc.setAccountType(d.type());
            acc.setAccountPurpose(d.purpose());
            acc.setActive(true);
            acc.setCreatedBy(actor);
            acc.setCreatedAt(new Date());
            accountRepo.save(acc);
            codes.add(code);
            purposes.add(d.purpose());
            added++;
        }
        if (added > 0) log.info("Added {} standard accounts for business {}", added, business.getId());
        return added;
    }

    /** The "Set up / refresh standard accounts" button. */
    @Transactional
    public List<Account> setupDefaultAccounts(Long businessId, String userEmail) {
        accessHelper.requireCapability(businessId, userEmail, Capability.MANAGE_ACCOUNTS);
        Business business = accessHelper.requireBusiness(businessId);
        ensureDefaults(business, userEmail);
        return accountRepo.findByBusinessOrderByAccountCodeAsc(business);
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    @Transactional
    public Account createAccount(Long businessId, AccountDTO dto, String userEmail) {
        accessHelper.requireCapability(businessId, userEmail, Capability.MANAGE_ACCOUNTS);
        Business business = accessHelper.requireBusiness(businessId);

        String code = dto.getAccountCode().trim();
        if (accountRepo.existsByBusinessAndAccountCode(business, code)) {
            throw new AppException(HttpStatus.CONFLICT, "Account code " + code + " is already used");
        }
        AccountPurpose purpose = purposeFor(dto);
        requireFreePurpose(business, purpose, null);

        Account account = new Account();
        account.setBusiness(business);
        account.setAccountCode(code);
        account.setAccountName(dto.getAccountName().trim());
        account.setAccountType(dto.getAccountType());
        account.setAccountPurpose(purpose);
        account.setActive(true);
        account.setCreatedBy(userEmail);
        account.setCreatedAt(new Date());
        return accountRepo.save(account);
    }

    /** All accounts, inactive ones included (they still carry history). */
    public List<Account> listAccounts(Long businessId, String userEmail) {
        accessHelper.requireCapability(businessId, userEmail, Capability.MANAGE_ACCOUNTS);
        Business business = accessHelper.requireBusiness(businessId);
        return accountRepo.findByBusinessOrderByAccountCodeAsc(business);
    }

    public Account getAccount(Long businessId, Long accountId, String userEmail) {
        accessHelper.requireCapability(businessId, userEmail, Capability.MANAGE_ACCOUNTS);
        Business business = accessHelper.requireBusiness(businessId);
        return requireAccount(business, accountId);
    }

    @Transactional
    public Account updateAccount(Long businessId, Long accountId, AccountDTO dto, String userEmail) {
        accessHelper.requireCapability(businessId, userEmail, Capability.MANAGE_ACCOUNTS);
        Business business = accessHelper.requireBusiness(businessId);
        Account account = requireAccount(business, accountId);

        String code = dto.getAccountCode().trim();
        if (!account.getAccountCode().equals(code) && accountRepo.existsByBusinessAndAccountCode(business, code)) {
            throw new AppException(HttpStatus.CONFLICT, "Account code " + code + " is already used");
        }
        boolean system = account.getAccountPurpose() != null && account.getAccountPurpose().isSystem();
        if (system) {
            // The system posts to these by purpose: the name and code can change, what they are for can't.
            if (dto.getAccountType() != account.getAccountType()
                    || (dto.getAccountPurpose() != null && dto.getAccountPurpose() != account.getAccountPurpose())) {
                throw new AppException(HttpStatus.BAD_REQUEST,
                        "'" + account.getAccountName() + "' is used by the system; only its name and code can change");
            }
            if (Boolean.FALSE.equals(dto.getActive())) {
                throw new AppException(HttpStatus.BAD_REQUEST,
                        "'" + account.getAccountName() + "' is used by the system and can't be switched off");
            }
        } else {
            AccountPurpose purpose = purposeFor(dto);
            requireFreePurpose(business, purpose, account.getId());
            account.setAccountType(dto.getAccountType());
            account.setAccountPurpose(purpose);
            if (dto.getActive() != null) account.setActive(dto.getActive());
        }
        account.setAccountCode(code);
        account.setAccountName(dto.getAccountName().trim());
        account.setUpdatedBy(userEmail);
        account.setUpdatedAt(new Date());
        return accountRepo.save(account);
    }

    // ── Lookups used by postings ─────────────────────────────────────────────

    public Optional<Account> findAccount(Business business, AccountPurpose purpose) {
        return accountRepo.findFirstByBusinessAndAccountPurposeAndActiveTrueOrderByIdAsc(business, purpose);
    }

    public Account findRequiredAccount(Business business, AccountPurpose purpose) {
        return findAccount(business, purpose).orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST,
                "No active account for " + purpose.name().toLowerCase().replace('_', ' ')
                        + ". Use 'Set up standard accounts' under Settings → Accounts."));
    }

    public Account requireAccount(Business business, Long accountId) {
        if (accountId == null) throw new AppException(HttpStatus.BAD_REQUEST, "Choose an account");
        return accountRepo.findByIdAndBusiness(accountId, business)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    public List<Account> activeAccounts(Business business) {
        return accountRepo.findByBusinessAndActiveTrueOrderByAccountCodeAsc(business);
    }

    /** Every account of a company, inactive ones too, by code (for reports; no permission check). */
    public List<Account> listAccountsOf(Business business) {
        return accountRepo.findByBusinessOrderByAccountCodeAsc(business);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Accounts people add are general ones unless a purpose is given; expenses default to EXPENSE. */
    private static AccountPurpose purposeFor(AccountDTO dto) {
        if (dto.getAccountPurpose() != null) return dto.getAccountPurpose();
        return dto.getAccountType() == AccountType.EXPENSE ? AccountPurpose.EXPENSE : AccountPurpose.OTHER;
    }

    private void requireFreePurpose(Business business, AccountPurpose purpose, Long exceptId) {
        if (purpose == null || !purpose.isSystem()) return;
        findAccount(business, purpose).filter(a -> !a.getId().equals(exceptId)).ifPresent(a -> {
            throw new AppException(HttpStatus.CONFLICT,
                    "'" + a.getAccountName() + "' is already the " + purpose.name().toLowerCase().replace('_', ' ') + " account");
        });
    }
}

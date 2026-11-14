package com.ultimate.inventory.Imports;

import com.ultimate.inventory.Constants.AccountPurpose;
import com.ultimate.inventory.Constants.AccountType;
import com.ultimate.inventory.Constants.Capability;
import com.ultimate.inventory.Constants.JournalTransactionType;
import com.ultimate.inventory.DTO.AccountDTO;
import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.Accounts.Account;
import com.ultimate.inventory.Model.Accounts.Journal;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Repository.Accounts.AccountRepository;
import com.ultimate.inventory.Repository.Accounts.JournalRepository;
import com.ultimate.inventory.Service.AccountService;
import com.ultimate.inventory.Service.BusinessAccessHelper;
import com.ultimate.inventory.Service.JournalService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * The chart of accounts and opening balances. New codes create accounts; codes you already have
 * only take their opening balance. All opening balances go into one journal, balanced against
 * the owner's capital (the same way opening stock enters the books).
 */
@Component
public class AccountImporter implements Importer {

    static final String REFERENCE_PREFIX = "OPENBAL-";

    /** Balances that come from somewhere else, so they can't be typed in here. */
    private static final Map<AccountPurpose, String> NO_BALANCE = Map.of(
            AccountPurpose.INVENTORY, "Stock value comes from your opening stock: upload products with opening quantities instead",
            AccountPurpose.ACCOUNTS_RECEIVABLE, "What customers owe can't be uploaded yet; record it as credit sales",
            AccountPurpose.ACCOUNTS_PAYABLE, "What you owe suppliers can't be uploaded yet; record it as credit purchases",
            AccountPurpose.CAPITAL, "Capital is worked out from your other opening balances");

    private static final List<ImportColumn> COLUMNS = List.of(
            ImportColumn.required("code", "Account code", "A code you already have (to give it an opening balance), or a new one.", "1010"),
            ImportColumn.required("name", "Account name", "For new accounts.", "Ecobank current account"),
            ImportColumn.optional("type", "Type", "For new accounts: required.", "Asset")
                    .withOptions(Arrays.stream(AccountType.values()).map(Cells::pretty).toList()),
            ImportColumn.optional("purpose", "Purpose", "What the system uses it for. Leave blank for an ordinary account. Each purpose other than Expense and Other belongs to one account only.", "")
                    .withOptions(Arrays.stream(AccountPurpose.values()).map(Cells::pretty).toList()),
            ImportColumn.optional("balance", "Opening balance", "What's in the account on the balance date. Assets, liabilities and equity only. Negative for an overdrawn account.", "15000.00"),
            ImportColumn.optional("date", "Balance date", "The date of the opening balances (yyyy-mm-dd), the same on every row. Blank = today.", "2026-01-01"));

    private final BusinessAccessHelper access;
    private final AccountRepository accountRepo;
    private final JournalRepository journalRepo;
    private final AccountService accountService;
    private final JournalService journalService;

    public AccountImporter(BusinessAccessHelper access, AccountRepository accountRepo, JournalRepository journalRepo,
                           AccountService accountService, JournalService journalService) {
        this.access = access;
        this.accountRepo = accountRepo;
        this.journalRepo = journalRepo;
        this.accountService = accountService;
        this.journalService = journalService;
    }

    @Override public ImportKind kind() { return ImportKind.ACCOUNTS; }
    @Override public List<ImportColumn> columns() { return COLUMNS; }

    @Override
    public List<String> notes() {
        return List.of(
                "Opening balances can be uploaded once. All of them go into one journal; the difference is recorded as the owner's capital.",
                "Stock, customer and supplier balances aren't entered here (see each one's note in the Purpose column help).");
    }

    @Override
    public void authorize(Long businessId, String email) {
        access.requireCapability(businessId, email, Capability.MANAGE_ACCOUNTS);
    }

    private record Balance(String code, BigDecimal amount) {}

    @Override
    public Plan prepare(Long businessId, List<SheetRow> rows, ImportResult result, String email) {
        Business business = access.requireBusiness(businessId);
        Map<String, Account> existing = new HashMap<>();
        Set<AccountPurpose> usedPurposes = EnumSet.noneOf(AccountPurpose.class);
        for (Account a : accountRepo.findByBusinessOrderByAccountCodeAsc(business)) {
            existing.put(a.getAccountCode().trim().toLowerCase(Locale.ROOT), a);
            if (a.getAccountPurpose() != null && a.getAccountPurpose().isSystem() && Boolean.TRUE.equals(a.getActive())) {
                usedPurposes.add(a.getAccountPurpose());
            }
        }
        Optional<Journal> earlier = journalRepo.findFirstByBusinessAndReferenceStartingWithOrderByIdAsc(business, REFERENCE_PREFIX);

        Set<String> seenCodes = new HashSet<>();
        Set<AccountPurpose> filePurposes = EnumSet.noneOf(AccountPurpose.class);
        List<Balance> balances = new ArrayList<>();
        LocalDate date = null;
        Plan plan = new Plan();
        // Accounts by code, including the ones this upload creates (filled in while saving).
        Map<String, Account> byCode = new HashMap<>(existing);

        for (SheetRow row : rows) {
            Cells c = new Cells(row, result, COLUMNS);
            String code = c.text("code", 20);
            String name = c.text("name", 120);
            AccountType type = c.choice("type", AccountType.class, Cells::pretty);
            AccountPurpose purpose = c.choice("purpose", AccountPurpose.class, Cells::pretty);
            BigDecimal balance = c.decimal("balance");
            LocalDate rowDate = c.date("date");
            if (rowDate != null) {
                if (date == null) date = rowDate;
                else if (!date.equals(rowDate)) c.error("date", "Every row must have the same balance date (" + date + ")");
            }
            if (code == null) continue;
            String k = code.toLowerCase(Locale.ROOT);
            if (!seenCodes.add(k)) {
                c.error("code", "Account " + code + " appears more than once in this file");
                continue;
            }
            Account current = existing.get(k);
            AccountType effectiveType = current != null ? current.getAccountType() : type;
            AccountPurpose effectivePurpose;
            if (current != null) {
                effectivePurpose = current.getAccountPurpose();
                if (type != null && type != current.getAccountType()) {
                    c.error("type", "Account " + code + " is already a " + Cells.pretty(current.getAccountType()).toLowerCase() + " account");
                }
            } else {
                if (type == null) c.error("type", "Required for a new account");
                effectivePurpose = purpose != null ? purpose : type == AccountType.EXPENSE ? AccountPurpose.EXPENSE : AccountPurpose.OTHER;
                if (effectivePurpose.isSystem()) {
                    if (usedPurposes.contains(effectivePurpose)) {
                        c.error("purpose", "You already have the " + Cells.pretty(effectivePurpose).toLowerCase() + " account");
                    } else if (!filePurposes.add(effectivePurpose)) {
                        c.error("purpose", Cells.pretty(effectivePurpose) + " appears more than once in this file");
                    }
                }
            }
            if (balance != null && balance.signum() != 0) {
                if (effectiveType == AccountType.REVENUE || effectiveType == AccountType.EXPENSE) {
                    c.error("balance", "Only asset, liability and equity accounts have opening balances");
                } else if (effectivePurpose != null && NO_BALANCE.containsKey(effectivePurpose)) {
                    c.error("balance", NO_BALANCE.get(effectivePurpose));
                } else if (earlier.isPresent()) {
                    c.error("balance", "Opening balances were already uploaded on "
                            + earlier.get().getTransactionDate().toLocalDate() + ". Make corrections with a manual journal.");
                } else if (balance.scale() > 2 && balance.setScale(2, RoundingMode.HALF_UP).compareTo(balance) != 0) {
                    c.error("balance", "Use at most 2 decimal places");
                }
            }
            if (!c.ok()) continue;

            if (current == null) {
                AccountDTO dto = new AccountDTO();
                dto.setAccountCode(code);
                dto.setAccountName(name);
                dto.setAccountType(type);
                dto.setAccountPurpose(effectivePurpose);
                dto.setActive(true);
                plan.add(row.number(), r -> {
                    byCode.put(k, accountService.createAccount(businessId, dto, email));
                    r.addCreated();
                });
            }
            if (balance != null && balance.signum() != 0) {
                balances.add(new Balance(k, balance.setScale(2, RoundingMode.HALF_UP)));
                if (current != null) plan.add(row.number(), ImportResult::addUpdated);
            }
        }

        if (!balances.isEmpty()) {
            LocalDate on = date != null ? date : LocalDate.now();
            if (on.isAfter(LocalDate.now())) result.fileError("The balance date can't be in the future");
            plan.add(0, r -> postOpeningBalances(business, balances, byCode, on, email));
            result.note("Opening balances will be posted as one journal dated " + on + ", balanced against the owner's capital.");
        }
        return plan;
    }

    private void postOpeningBalances(Business business, List<Balance> balances, Map<String, Account> byCode,
                                     LocalDate on, String email) {
        List<JournalService.EntryLine> lines = new ArrayList<>();
        BigDecimal debits = BigDecimal.ZERO, credits = BigDecimal.ZERO;
        for (Balance b : balances) {
            Account a = byCode.get(b.code());
            // Assets normally have debit balances; liabilities and equity credit balances.
            boolean debit = (a.getAccountType() == AccountType.ASSET) == (b.amount().signum() > 0);
            BigDecimal amount = b.amount().abs();
            if (debit) {
                lines.add(JournalService.EntryLine.debit(a, amount, "Opening balance"));
                debits = debits.add(amount);
            } else {
                lines.add(JournalService.EntryLine.credit(a, amount, "Opening balance"));
                credits = credits.add(amount);
            }
        }
        BigDecimal diff = debits.subtract(credits);
        if (diff.signum() > 0) {
            lines.add(new JournalService.EntryLine(AccountPurpose.CAPITAL, BigDecimal.ZERO, diff, "Owner's capital at opening"));
        } else if (diff.signum() < 0) {
            lines.add(new JournalService.EntryLine(AccountPurpose.CAPITAL, diff.negate(), BigDecimal.ZERO, "Owner's capital at opening"));
        }
        String ref = REFERENCE_PREFIX + Long.toString(System.currentTimeMillis(), 36).toUpperCase();
        Journal j = journalService.post(new JournalService.Source(business, null, JournalTransactionType.OPENING_BALANCE,
                ref, "Opening balances (uploaded)", null, on.atStartOfDay()), lines, email);
        if (j == null) {
            throw new AppException(HttpStatus.CONFLICT, "The opening balances couldn't be posted. Check that the company has a capital account");
        }
    }
}

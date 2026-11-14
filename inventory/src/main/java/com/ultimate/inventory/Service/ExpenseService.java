package com.ultimate.inventory.Service;

import com.ultimate.inventory.Constants.*;
import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.Accounts.Account;
import com.ultimate.inventory.Model.Accounts.Journal;
import com.ultimate.inventory.Model.Accounts.JournalEntry;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.BusinessUser;
import com.ultimate.inventory.Model.Expense;
import com.ultimate.inventory.Model.Shop;
import com.ultimate.inventory.Repository.Accounts.JournalEntryRepository;
import com.ultimate.inventory.Repository.Accounts.JournalRepository;
import com.ultimate.inventory.Repository.ExpenseRepository;
import com.ultimate.inventory.Repository.ShopRepository;
import com.ultimate.inventory.Service.JournalService.EntryLine;
import com.ultimate.inventory.Service.JournalService.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Business expenses (electricity, water, transport…), paid from the company's petty cash or
 * another money account. Cashiers record them for a manager to approve; managers' and admins'
 * are approved at once. Only approved expenses reach the books.
 */
// Reads share one read-only transaction (one connection, one commit, cached lookups); writes declare their own.
@Transactional(readOnly = true)
@Service
public class ExpenseService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseService.class);
    private static final ZoneId ZONE = ZoneId.of("Africa/Accra");

    public static final String PENDING = "PENDING", APPROVED = "APPROVED", REJECTED = "REJECTED",
            CANCELLED = "CANCELLED", VOID = "VOID";

    private final BusinessAccessHelper accessHelper;
    private final AccountService accountService;
    private final AccountingService accountingService;
    private final JournalService journalService;
    private final ExpenseRepository expenseRepo;
    private final ShopRepository shopRepo;
    private final JournalRepository journalRepo;
    private final JournalEntryRepository entryRepo;

    public ExpenseService(BusinessAccessHelper accessHelper, AccountService accountService,
                          AccountingService accountingService, JournalService journalService,
                          ExpenseRepository expenseRepo, ShopRepository shopRepo,
                          JournalRepository journalRepo, JournalEntryRepository entryRepo) {
        this.accessHelper = accessHelper;
        this.accountService = accountService;
        this.accountingService = accountingService;
        this.journalService = journalService;
        this.expenseRepo = expenseRepo;
        this.shopRepo = shopRepo;
        this.journalRepo = journalRepo;
        this.entryRepo = entryRepo;
    }

    // ── Shapes ───────────────────────────────────────────────────────────────

    public record NewExpense(Long shopId, Long expenseAccountId, Long paidFromAccountId, BigDecimal amount,
                             LocalDate date, String description, String payee, String receiptNumber) {}
    public record Decision(String note) {}

    public record Option(Long id, String code, String name, BigDecimal balance) {}
    public record Branch(Long id, String name) {}
    /** What the "record an expense" form needs for this person. */
    public record Options(List<Option> expenseAccounts, List<Option> paidFrom, Long defaultPaidFromId,
                          List<Branch> branches, boolean needsApproval, boolean canApprove) {}

    public record ExpenseView(Long id, String reference, LocalDate date, String description, String payee,
                              String receiptNumber, BigDecimal amount, String status,
                              Long shopId, String shopName, Long expenseAccountId, String expenseAccountName,
                              Long paidFromId, String paidFromName, String requestedBy, Date requestedAt,
                              String decidedBy, Date decidedAt, String decisionNote,
                              boolean canApprove, boolean canCancel, boolean canVoid) {}

    // ── Commands ─────────────────────────────────────────────────────────────

    @Transactional
    public ExpenseView create(Long businessId, NewExpense req, String email) {
        BusinessUser member = accessHelper.requireCapability(businessId, email, Capability.RECORD_EXPENSES);
        Business business = accessHelper.requireBusiness(businessId);
        boolean manager = BusinessAccessHelper.isManager(member);

        BigDecimal amount = req.amount() != null ? req.amount().setScale(2, RoundingMode.HALF_UP) : null;
        if (amount == null || amount.signum() <= 0) throw bad("Enter an amount above zero");
        if (req.description() == null || req.description().isBlank()) throw bad("Say what the money was spent on");
        LocalDate date = req.date() != null ? req.date() : LocalDate.now(ZONE);
        if (date.isAfter(LocalDate.now(ZONE))) throw bad("The date can't be in the future");

        Shop shop = null;
        if (req.shopId() != null) {
            shop = accessHelper.requireShopAccess(req.shopId(), businessId, member);
        } else if (!manager) {
            List<Branch> mine = branches(business, member);
            if (mine.size() != 1) throw bad("Choose the branch this was for");
            shop = accessHelper.requireShopInBusiness(mine.get(0).id(), businessId);
        }

        Account expenseAccount = accountService.requireAccount(business, req.expenseAccountId());
        if (!isExpenseChoice(expenseAccount)) throw bad("Choose what the expense was for (e.g. Electricity)");

        Account paidFrom = req.paidFromAccountId() != null
                ? accountService.requireAccount(business, req.paidFromAccountId())
                : accountService.findRequiredAccount(business, AccountPurpose.PETTY_CASH);
        if (!allowedPaidFrom(member).contains(paidFrom.getAccountPurpose()) || !Boolean.TRUE.equals(paidFrom.getActive())) {
            throw bad("You can't pay expenses from " + paidFrom.getAccountName());
        }

        Expense e = new Expense();
        e.setBusiness(business);
        e.setShop(shop);
        e.setExpenseAccount(expenseAccount);
        e.setPaidFrom(paidFrom);
        e.setAmount(amount);
        e.setExpenseDate(date);
        e.setDescription(req.description().trim());
        e.setPayee(trim(req.payee()));
        e.setReceiptNumber(trim(req.receiptNumber()));
        e.setRequestedBy(email);
        e.setStatus(PENDING);
        e.setCreatedBy(email);
        e.setCreatedAt(new Date());
        e = expenseRepo.save(e);
        e.setReference("EXP-" + String.format("%05d", e.getId()));

        if (manager) approveNow(e, email, null); // managers and admins don't need anyone's approval
        else log.info("Expense {} ({} {}) recorded by {}, waiting for approval", e.getReference(), amount,
                expenseAccount.getAccountName(), email);
        return view(expenseRepo.save(e), member);
    }

    @Transactional
    public ExpenseView approve(Long businessId, Long expenseId, Decision d, String email) {
        BusinessUser member = accessHelper.requireCapability(businessId, email, Capability.APPROVE);
        Expense e = requirePending(businessId, expenseId);
        approveNow(e, email, d != null ? trim(d.note()) : null);
        return view(expenseRepo.save(e), member);
    }

    @Transactional
    public ExpenseView reject(Long businessId, Long expenseId, Decision d, String email) {
        BusinessUser member = accessHelper.requireCapability(businessId, email, Capability.APPROVE);
        Expense e = requirePending(businessId, expenseId);
        e.setStatus(REJECTED);
        decided(e, email, d != null ? trim(d.note()) : null);
        log.info("Expense {} rejected by {}", e.getReference(), email);
        return view(expenseRepo.save(e), member);
    }

    /** The person who recorded it (or a manager) withdraws an expense still waiting for approval. */
    @Transactional
    public ExpenseView cancel(Long businessId, Long expenseId, String email) {
        BusinessUser member = accessHelper.requireMembership(businessId, email);
        Expense e = requirePending(businessId, expenseId);
        if (!email.equalsIgnoreCase(e.getRequestedBy()) && !BusinessAccessHelper.can(member, Capability.APPROVE)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only the person who recorded it or a manager can cancel it");
        }
        e.setStatus(CANCELLED);
        decided(e, email, null);
        return view(expenseRepo.save(e), member);
    }

    /** Undoes an approved expense (wrong amount, recorded twice…): its entry is reversed, the record stays. */
    @Transactional
    public ExpenseView voidExpense(Long businessId, Long expenseId, Decision d, String email) {
        BusinessUser member = accessHelper.requireCapability(businessId, email, Capability.MANAGE_ACCOUNTS);
        Business business = accessHelper.requireBusiness(businessId);
        Expense e = expenseRepo.findByIdAndBusiness(expenseId, business)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Expense not found"));
        if (!APPROVED.equals(e.getStatus())) throw bad("Only approved expenses can be voided");
        if (e.getJournalId() != null) {
            Journal original = journalRepo.findById(e.getJournalId()).orElse(null);
            if (original != null) {
                List<EntryLine> lines = new ArrayList<>();
                for (JournalEntry je : entryRepo.findByJournal(original)) {
                    lines.add(new EntryLine(je.getAccount().getAccountPurpose(), je.getAccount(), je.getCredit(),
                            je.getDebit(), "Reversal"));
                }
                journalService.post(new Source(business, e.getShop(), JournalTransactionType.EXPENSE_CASH,
                        e.getReference() + "-VOID", "Void of " + e.getReference() + ": " + e.getDescription(),
                        e.getId(), LocalDateTime.now(ZONE)), lines, email);
            }
        }
        e.setStatus(VOID);
        decided(e, email, d != null ? trim(d.note()) : null);
        log.info("Expense {} voided by {}", e.getReference(), email);
        return view(expenseRepo.save(e), member);
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    /** Managers see every expense; others only the ones they recorded. */
    public Page<ExpenseView> list(Long businessId, String status, Long shopId, int page, int size, String email) {
        BusinessUser member = accessHelper.requireCapability(businessId, email,
                Capability.RECORD_EXPENSES, Capability.APPROVE);
        Business business = accessHelper.requireBusiness(businessId);
        String mine = BusinessAccessHelper.can(member, Capability.APPROVE) ? null : email;
        String st = status != null && !status.isBlank() ? status.trim().toUpperCase() : null;
        return expenseRepo.search(business, st, shopId, mine,
                        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                                Sort.by(Sort.Direction.DESC, "id")))
                .map(e -> view(e, member));
    }

    public long pendingCount(Long businessId, String email) {
        BusinessUser member = accessHelper.requireMembership(businessId, email);
        if (!BusinessAccessHelper.can(member, Capability.APPROVE)) return 0;
        return expenseRepo.countByBusinessAndStatus(accessHelper.requireBusiness(businessId), PENDING);
    }

    public Options options(Long businessId, String email) {
        BusinessUser member = accessHelper.requireCapability(businessId, email, Capability.RECORD_EXPENSES);
        Business business = accessHelper.requireBusiness(businessId);
        List<Option> expenses = new ArrayList<>(), from = new ArrayList<>();
        Set<AccountPurpose> allowed = allowedPaidFrom(member);
        boolean seesBalances = BusinessAccessHelper.isManager(member);
        Long defaultFrom = null;
        for (Account a : accountService.activeAccounts(business)) {
            if (isExpenseChoice(a)) {
                expenses.add(new Option(a.getId(), a.getAccountCode(), a.getAccountName(), null));
            } else if (a.getAccountPurpose() != null && allowed.contains(a.getAccountPurpose())) {
                from.add(new Option(a.getId(), a.getAccountCode(), a.getAccountName(),
                        seesBalances ? accountingService.balanceOf(a) : null));
                if (a.getAccountPurpose() == AccountPurpose.PETTY_CASH && defaultFrom == null) defaultFrom = a.getId();
            }
        }
        return new Options(expenses, from, defaultFrom, branches(business, member),
                !BusinessAccessHelper.isManager(member), BusinessAccessHelper.can(member, Capability.APPROVE));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Posts the expense: the expense account goes up, the money account it was paid from goes down. */
    private void approveNow(Expense e, String approver, String note) {
        Account from = e.getPaidFrom();
        if (from.getAccountPurpose() != AccountPurpose.BANK) {
            BigDecimal bal = accountingService.balanceOf(from);
            if (bal.compareTo(e.getAmount()) < 0) {
                throw bad(from.getAccountName() + " has only " + bal.setScale(2, RoundingMode.HALF_UP)
                        + ". Top it up (Accounts → Move money) before approving this "
                        + e.getAmount().setScale(2, RoundingMode.HALF_UP) + " expense.");
            }
        }
        String desc = e.getExpenseAccount().getAccountName() + ": " + e.getDescription()
                + (e.getPayee() != null ? " (" + e.getPayee() + ")" : "");
        LocalDate today = LocalDate.now(ZONE);
        LocalDateTime when = e.getExpenseDate().equals(today) ? LocalDateTime.now(ZONE) : e.getExpenseDate().atTime(12, 0);
        Journal j = journalService.post(new Source(e.getBusiness(), e.getShop(), JournalTransactionType.EXPENSE_CASH,
                e.getReference(), desc, e.getId(), when), List.of(
                EntryLine.debit(e.getExpenseAccount(), e.getAmount(), e.getDescription()),
                EntryLine.credit(from, e.getAmount(), "Paid from " + from.getAccountName())), approver);
        if (j == null) throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "The expense could not be posted");
        e.setJournalId(j.getId());
        e.setStatus(APPROVED);
        decided(e, approver, note);
        log.info("Expense {} approved by {}: {} {}", e.getReference(), approver, e.getAmount(), desc);
    }

    private Expense requirePending(Long businessId, Long expenseId) {
        Business business = accessHelper.requireBusiness(businessId);
        Expense e = expenseRepo.findByIdAndBusiness(expenseId, business)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Expense not found"));
        if (!PENDING.equals(e.getStatus())) throw bad("This expense is already " + e.getStatus().toLowerCase());
        return e;
    }

    private static void decided(Expense e, String who, String note) {
        e.setDecidedBy(who);
        e.setDecidedAt(new Date());
        e.setDecisionNote(note);
        e.setUpdatedBy(who);
        e.setUpdatedAt(new Date());
    }

    /** Accounts a person may pay expenses from: cashiers only petty cash, managers also the till, admins any. */
    private static Set<AccountPurpose> allowedPaidFrom(BusinessUser member) {
        if (BusinessAccessHelper.can(member, Capability.MANAGE_ACCOUNTS)) {
            return EnumSet.of(AccountPurpose.PETTY_CASH, AccountPurpose.CASH, AccountPurpose.BANK, AccountPurpose.MOBILE_MONEY);
        }
        if (BusinessAccessHelper.isManager(member)) return EnumSet.of(AccountPurpose.PETTY_CASH, AccountPurpose.CASH);
        return EnumSet.of(AccountPurpose.PETTY_CASH);
    }

    /** Running costs people can book an expense to (not cost of goods, stock losses and the like). */
    private static boolean isExpenseChoice(Account a) {
        return a.getAccountType() == AccountType.EXPENSE && Boolean.TRUE.equals(a.getActive())
                && (a.getAccountPurpose() == null || a.getAccountPurpose() == AccountPurpose.EXPENSE
                    || a.getAccountPurpose() == AccountPurpose.OTHER);
    }

    private List<Branch> branches(Business business, BusinessUser member) {
        Set<Long> allowed = accessHelper.allowedShopIds(member);
        List<Branch> out = new ArrayList<>();
        for (Shop s : shopRepo.findActiveByBusiness(business, PageRequest.of(0, 200)).getContent()) {
            if (allowed == null || allowed.contains(s.getId())) out.add(new Branch(s.getId(), s.getShopName()));
        }
        return out;
    }

    private ExpenseView view(Expense e, BusinessUser viewer) {
        boolean approver = BusinessAccessHelper.can(viewer, Capability.APPROVE);
        boolean pending = PENDING.equals(e.getStatus());
        String email = viewer.getUser() != null ? viewer.getUser().getEmail() : null;
        return new ExpenseView(e.getId(), e.getReference(), e.getExpenseDate(), e.getDescription(), e.getPayee(),
                e.getReceiptNumber(), e.getAmount(), e.getStatus(),
                e.getShop() != null ? e.getShop().getId() : null, e.getShop() != null ? e.getShop().getShopName() : null,
                e.getExpenseAccount().getId(), e.getExpenseAccount().getAccountName(),
                e.getPaidFrom().getId(), e.getPaidFrom().getAccountName(),
                e.getRequestedBy(), e.getCreatedAt(), e.getDecidedBy(), e.getDecidedAt(), e.getDecisionNote(),
                pending && approver,
                pending && (approver || (email != null && email.equalsIgnoreCase(e.getRequestedBy()))),
                APPROVED.equals(e.getStatus()) && BusinessAccessHelper.can(viewer, Capability.MANAGE_ACCOUNTS));
    }

    private static String trim(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private static AppException bad(String m) { return new AppException(HttpStatus.BAD_REQUEST, m); }
}

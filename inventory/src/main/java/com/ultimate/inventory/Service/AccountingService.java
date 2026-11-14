package com.ultimate.inventory.Service;

import com.ultimate.inventory.Constants.*;
import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.Accounts.Account;
import com.ultimate.inventory.Model.Accounts.Journal;
import com.ultimate.inventory.Model.Accounts.JournalEntry;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.Invoice;
import com.ultimate.inventory.Model.InvoiceDetail;
import com.ultimate.inventory.Model.Payment;
import com.ultimate.inventory.Repository.Accounts.JournalEntryRepository;
import com.ultimate.inventory.Repository.Accounts.JournalRepository;
import com.ultimate.inventory.Repository.ExpenseRepository;
import com.ultimate.inventory.Repository.InvoiceDetailRepository;
import com.ultimate.inventory.Repository.InvoiceRepository;
import com.ultimate.inventory.Repository.PaymentRepository;
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
 * The money side of a company: what the owner puts in and takes out, moving money between its own
 * accounts (e.g. topping up petty cash), manual entries, and the reports (ledger, trial balance,
 * profit &amp; loss, balance sheet). Sales, purchases, payments, stock write-offs and expenses post
 * their own entries automatically (JournalService / ExpenseService).
 */
// Reads share one read-only transaction (one connection, one commit, cached lookups); writes declare their own.
@Transactional(readOnly = true)
@Service
public class AccountingService {

    private static final Logger log = LoggerFactory.getLogger(AccountingService.class);
    private static final ZoneId ZONE = ZoneId.of("Africa/Accra");
    private static final LocalDateTime BEGINNING = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(9999, 1, 1, 0, 0);

    private final BusinessAccessHelper accessHelper;
    private final AccountService accountService;
    private final JournalService journalService;
    private final JournalRepository journalRepo;
    private final JournalEntryRepository entryRepo;
    private final InvoiceRepository invoiceRepo;
    private final InvoiceDetailRepository detailRepo;
    private final PaymentRepository paymentRepo;
    private final ExpenseRepository expenseRepo;

    public AccountingService(BusinessAccessHelper accessHelper, AccountService accountService,
                             JournalService journalService, JournalRepository journalRepo,
                             JournalEntryRepository entryRepo, InvoiceRepository invoiceRepo,
                             InvoiceDetailRepository detailRepo, PaymentRepository paymentRepo,
                             ExpenseRepository expenseRepo) {
        this.accessHelper = accessHelper;
        this.accountService = accountService;
        this.journalService = journalService;
        this.journalRepo = journalRepo;
        this.entryRepo = entryRepo;
        this.invoiceRepo = invoiceRepo;
        this.detailRepo = detailRepo;
        this.paymentRepo = paymentRepo;
        this.expenseRepo = expenseRepo;
    }

    // ── Shapes ───────────────────────────────────────────────────────────────

    public record AccountRef(Long id, String code, String name, AccountType type, AccountPurpose purpose) {
        static AccountRef of(Account a) {
            return new AccountRef(a.getId(), a.getAccountCode(), a.getAccountName(), a.getAccountType(), a.getAccountPurpose());
        }
    }
    public record Balance(AccountRef account, BigDecimal balance) {}

    /** Owner putting money in (capital) or taking it out (drawings). Give the account, or a payment method. */
    public record OwnerMoney(BigDecimal amount, Long accountId, PaymentMethod method, LocalDate date, String note) {}
    public record Transfer(Long fromAccountId, Long toAccountId, BigDecimal amount, LocalDate date, String note) {}
    public record ManualLine(Long accountId, BigDecimal debit, BigDecimal credit, String memo) {}
    public record ManualJournal(LocalDate date, String description, String reference, List<ManualLine> lines) {}

    public record JournalLineView(Long accountId, String accountCode, String accountName, BigDecimal debit,
                                  BigDecimal credit, String description) {}
    public record JournalView(Long id, LocalDateTime date, String reference, String description,
                              JournalTransactionType type, String shopName, String createdBy, BigDecimal total,
                              List<JournalLineView> lines) {}

    public record LedgerLine(Long journalId, LocalDateTime date, String reference, String description,
                             BigDecimal debit, BigDecimal credit, BigDecimal balance) {}
    public record Ledger(AccountRef account, LocalDate from, LocalDate to, BigDecimal opening,
                         BigDecimal totalDebit, BigDecimal totalCredit, BigDecimal closing, List<LedgerLine> lines) {}

    public record TrialRow(AccountRef account, BigDecimal debit, BigDecimal credit) {}
    public record TrialBalance(LocalDate asOf, List<TrialRow> rows, BigDecimal totalDebit, BigDecimal totalCredit,
                               boolean balanced) {}

    public record ReportRow(AccountRef account, BigDecimal amount) {}
    public record ProfitAndLoss(LocalDate from, LocalDate to, List<ReportRow> income, BigDecimal totalIncome,
                                List<ReportRow> costOfSales, BigDecimal totalCostOfSales, BigDecimal grossProfit,
                                List<ReportRow> expenses, BigDecimal totalExpenses, BigDecimal netProfit) {}
    public record BalanceSheet(LocalDate asOf, List<ReportRow> assets, BigDecimal totalAssets,
                               List<ReportRow> liabilities, BigDecimal totalLiabilities,
                               List<ReportRow> equity, BigDecimal profitToDate, BigDecimal totalEquity,
                               BigDecimal totalLiabilitiesAndEquity, boolean balanced) {}

    public record Summary(List<Balance> money, BigDecimal totalMoney, BigDecimal receivables, BigDecimal payables,
                          BigDecimal stockValue, BigDecimal capital, LocalDate monthStart,
                          BigDecimal monthIncome, BigDecimal monthCosts, BigDecimal monthProfit,
                          long pendingExpenses, int invoicesNotInBooks) {}

    public record CatchUp(int invoicesPosted, int paymentsPosted, int skipped, List<String> notes) {}

    // ── Owner's money ────────────────────────────────────────────────────────

    /** The owner puts money into the business: money account up, Owner's Capital up. */
    @Transactional
    public JournalView recordCapital(Long businessId, OwnerMoney req, String email) {
        accessHelper.requireCapability(businessId, email, Capability.MANAGE_ACCOUNTS);
        Business business = accessHelper.requireBusiness(businessId);
        BigDecimal amount = positive(req.amount());
        Account money = moneyAccount(business, req.accountId(), req.method());
        Account capital = accountService.findRequiredAccount(business, AccountPurpose.CAPITAL);
        String note = blank(req.note()) ? "Capital introduced by the owner" : req.note().trim();
        Journal j = post(business, JournalTransactionType.OWNER_CAPITAL, "CAP", note, req.date(), List.of(
                EntryLine.debit(money, amount, "Capital received into " + money.getAccountName()),
                EntryLine.credit(capital, amount, note)), email);
        return view(j);
    }

    /** The owner takes money out for personal use: money account down, Drawings up. */
    @Transactional
    public JournalView recordDrawings(Long businessId, OwnerMoney req, String email) {
        accessHelper.requireCapability(businessId, email, Capability.MANAGE_ACCOUNTS);
        Business business = accessHelper.requireBusiness(businessId);
        BigDecimal amount = positive(req.amount());
        Account money = moneyAccount(business, req.accountId(), req.method());
        requireEnough(business, money, amount);
        Account drawings = accountService.findRequiredAccount(business, AccountPurpose.DRAWINGS);
        String note = blank(req.note()) ? "Owner's drawings" : req.note().trim();
        Journal j = post(business, JournalTransactionType.OWNER_DRAWINGS, "DRW", note, req.date(), List.of(
                EntryLine.debit(drawings, amount, note),
                EntryLine.credit(money, amount, "Paid out of " + money.getAccountName())), email);
        return view(j);
    }

    /** Moves money between the company's own accounts, e.g. topping up petty cash or banking the takings. */
    @Transactional
    public JournalView transfer(Long businessId, Transfer req, String email) {
        accessHelper.requireCapability(businessId, email, Capability.MANAGE_ACCOUNTS);
        Business business = accessHelper.requireBusiness(businessId);
        BigDecimal amount = positive(req.amount());
        Account from = requireMoney(business, req.fromAccountId());
        Account to = requireMoney(business, req.toAccountId());
        if (from.getId().equals(to.getId())) throw bad("Choose two different accounts");
        requireEnough(business, from, amount);
        String note = blank(req.note()) ? from.getAccountName() + " → " + to.getAccountName() : req.note().trim();
        Journal j = post(business, JournalTransactionType.FUND_TRANSFER, "TRF", note, req.date(), List.of(
                EntryLine.debit(to, amount, "From " + from.getAccountName()),
                EntryLine.credit(from, amount, "To " + to.getAccountName())), email);
        return view(j);
    }

    /** An entry typed in by an accountant (opening balances, corrections…). Must balance. */
    @Transactional
    public JournalView manualJournal(Long businessId, ManualJournal req, String email) {
        accessHelper.requireCapability(businessId, email, Capability.MANAGE_ACCOUNTS);
        Business business = accessHelper.requireBusiness(businessId);
        if (blank(req.description())) throw bad("Describe the entry");
        if (req.lines() == null || req.lines().size() < 2) throw bad("An entry needs at least two lines");
        List<EntryLine> lines = new ArrayList<>();
        BigDecimal dr = BigDecimal.ZERO, cr = BigDecimal.ZERO;
        for (ManualLine l : req.lines()) {
            BigDecimal d = money(l.debit()), c = money(l.credit());
            if (d.signum() < 0 || c.signum() < 0) throw bad("Amounts can't be negative");
            if (d.signum() > 0 && c.signum() > 0) throw bad("A line is either a debit or a credit, not both");
            if (d.signum() == 0 && c.signum() == 0) continue;
            Account a = accountService.requireAccount(business, l.accountId());
            if (!Boolean.TRUE.equals(a.getActive())) throw bad("'" + a.getAccountName() + "' is switched off");
            lines.add(new EntryLine(a.getAccountPurpose(), a, d, c, blank(l.memo()) ? req.description() : l.memo()));
            dr = dr.add(d);
            cr = cr.add(c);
        }
        if (lines.size() < 2) throw bad("An entry needs at least two lines with amounts");
        if (dr.compareTo(cr) != 0) throw bad("Debits (" + dr + ") and credits (" + cr + ") must be equal");
        String ref = blank(req.reference()) ? null : req.reference().trim();
        Journal j = post(business, JournalTransactionType.MANUAL, ref == null ? "JNL" : null, req.description().trim(),
                req.date(), lines, email, ref);
        return view(j);
    }

    // ── Reading the books ────────────────────────────────────────────────────

    public List<AccountRef> moneyAccounts(Long businessId, String email) {
        accessHelper.requireCapability(businessId, email, Capability.MANAGE_ACCOUNTS);
        Business business = accessHelper.requireBusiness(businessId);
        return accountService.activeAccounts(business).stream()
                .filter(a -> a.getAccountPurpose() != null && a.getAccountPurpose().isMoney())
                .map(AccountRef::of).toList();
    }

    public Page<JournalView> journals(Long businessId, JournalTransactionType type, LocalDate from, LocalDate to,
                                      int page, int size, String email) {
        accessHelper.requireCapability(businessId, email, Capability.MANAGE_ACCOUNTS);
        Business business = accessHelper.requireBusiness(businessId);
        Page<Journal> journals = journalRepo.search(business, type, start(from), end(to),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "transactionDate").and(Sort.by(Sort.Direction.DESC, "id"))));
        Map<Long, List<JournalEntry>> lines = new HashMap<>();
        if (!journals.isEmpty()) {
            for (JournalEntry e : entryRepo.findByJournalIn(journals.getContent())) {
                lines.computeIfAbsent(e.getJournal().getId(), k -> new ArrayList<>()).add(e);
            }
        }
        return journals.map(j -> view(j, lines.getOrDefault(j.getId(), List.of())));
    }

    public Ledger ledger(Long businessId, Long accountId, LocalDate from, LocalDate to, String email) {
        accessHelper.requireCapability(businessId, email, Capability.MANAGE_ACCOUNTS);
        Business business = accessHelper.requireBusiness(businessId);
        Account account = accountService.requireAccount(business, accountId);
        LocalDate f = from != null ? from : LocalDate.now(ZONE).withDayOfMonth(1);
        LocalDate t = to != null ? to : LocalDate.now(ZONE);
        BigDecimal[] before = entryRepo.totalsBeforeOf(account, start(f));
        BigDecimal opening = signed(account, before[0], before[1]);
        BigDecimal running = opening, totalDr = BigDecimal.ZERO, totalCr = BigDecimal.ZERO;
        List<LedgerLine> out = new ArrayList<>();
        for (JournalEntry e : entryRepo.ledger(account, start(f), end(t))) {
            running = running.add(signed(account, e.getDebit(), e.getCredit()));
            totalDr = totalDr.add(e.getDebit());
            totalCr = totalCr.add(e.getCredit());
            Journal j = e.getJournal();
            out.add(new LedgerLine(j.getId(), j.getTransactionDate(), j.getReference(),
                    e.getDescription() != null && !e.getDescription().isBlank() ? j.getDescription() + " — " + e.getDescription()
                            : j.getDescription(),
                    e.getDebit(), e.getCredit(), running));
        }
        return new Ledger(AccountRef.of(account), f, t, opening, totalDr, totalCr, running, out);
    }

    public TrialBalance trialBalance(Long businessId, LocalDate asOf, String email) {
        accessHelper.requireCapability(businessId, email, Capability.MANAGE_ACCOUNTS);
        Business business = accessHelper.requireBusiness(businessId);
        LocalDate d = asOf != null ? asOf : LocalDate.now(ZONE);
        Map<Long, BigDecimal[]> totals = totals(business, BEGINNING, end(d));
        List<TrialRow> rows = new ArrayList<>();
        BigDecimal tDr = BigDecimal.ZERO, tCr = BigDecimal.ZERO;
        for (Account a : accountService.listAccountsOf(business)) {
            BigDecimal[] t = totals.get(a.getId());
            if (t == null) continue;
            BigDecimal net = t[0].subtract(t[1]); // debit − credit
            if (net.signum() == 0) continue;
            BigDecimal dr = net.signum() > 0 ? net : BigDecimal.ZERO;
            BigDecimal cr = net.signum() < 0 ? net.negate() : BigDecimal.ZERO;
            rows.add(new TrialRow(AccountRef.of(a), dr, cr));
            tDr = tDr.add(dr);
            tCr = tCr.add(cr);
        }
        return new TrialBalance(d, rows, tDr, tCr, tDr.compareTo(tCr) == 0);
    }

    public ProfitAndLoss profitAndLoss(Long businessId, LocalDate from, LocalDate to, String email) {
        accessHelper.requireCapability(businessId, email, Capability.MANAGE_ACCOUNTS);
        Business business = accessHelper.requireBusiness(businessId);
        LocalDate f = from != null ? from : LocalDate.now(ZONE).withDayOfMonth(1);
        LocalDate t = to != null ? to : LocalDate.now(ZONE);
        return pnl(business, f, t);
    }

    public BalanceSheet balanceSheet(Long businessId, LocalDate asOf, String email) {
        accessHelper.requireCapability(businessId, email, Capability.MANAGE_ACCOUNTS);
        Business business = accessHelper.requireBusiness(businessId);
        LocalDate d = asOf != null ? asOf : LocalDate.now(ZONE);
        Map<Long, BigDecimal[]> totals = totals(business, BEGINNING, end(d));
        List<ReportRow> assets = new ArrayList<>(), liabilities = new ArrayList<>(), equity = new ArrayList<>();
        BigDecimal tA = BigDecimal.ZERO, tL = BigDecimal.ZERO, tE = BigDecimal.ZERO, profit = BigDecimal.ZERO;
        for (Account a : accountService.listAccountsOf(business)) {
            BigDecimal[] t = totals.get(a.getId());
            if (t == null) continue;
            BigDecimal bal = signed(a, t[0], t[1]);
            switch (a.getAccountType()) {
                case ASSET -> { if (bal.signum() != 0) assets.add(new ReportRow(AccountRef.of(a), bal)); tA = tA.add(bal); }
                case LIABILITY -> { if (bal.signum() != 0) liabilities.add(new ReportRow(AccountRef.of(a), bal)); tL = tL.add(bal); }
                case EQUITY -> { if (bal.signum() != 0) equity.add(new ReportRow(AccountRef.of(a), bal)); tE = tE.add(bal); }
                // Income and costs not yet closed into retained earnings are this business's profit to date.
                case REVENUE -> profit = profit.add(bal);
                case EXPENSE -> profit = profit.subtract(bal);
            }
        }
        BigDecimal totalEquity = tE.add(profit);
        BigDecimal le = tL.add(totalEquity);
        return new BalanceSheet(d, assets, tA, liabilities, tL, equity, profit, totalEquity, le, tA.compareTo(le) == 0);
    }

    /** The accounting home page: money on hand, who owes what, this month's result. */
    public Summary summary(Long businessId, String email) {
        accessHelper.requireCapability(businessId, email, Capability.MANAGE_ACCOUNTS);
        Business business = accessHelper.requireBusiness(businessId);
        Map<Long, BigDecimal[]> totals = totals(business, BEGINNING, END);
        List<Balance> money = new ArrayList<>();
        BigDecimal totalMoney = BigDecimal.ZERO, ar = BigDecimal.ZERO, ap = BigDecimal.ZERO,
                stock = BigDecimal.ZERO, capital = BigDecimal.ZERO;
        for (Account a : accountService.listAccountsOf(business)) {
            BigDecimal[] t = totals.getOrDefault(a.getId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal bal = signed(a, t[0], t[1]);
            AccountPurpose p = a.getAccountPurpose();
            if (p == null) continue;
            if (p.isMoney() && Boolean.TRUE.equals(a.getActive())) {
                money.add(new Balance(AccountRef.of(a), bal));
                totalMoney = totalMoney.add(bal);
            }
            if (p == AccountPurpose.ACCOUNTS_RECEIVABLE) ar = ar.add(bal);
            if (p == AccountPurpose.ACCOUNTS_PAYABLE) ap = ap.add(bal);
            if (p == AccountPurpose.INVENTORY) stock = stock.add(bal);
            if (p == AccountPurpose.CAPITAL) capital = capital.add(bal);
        }
        LocalDate monthStart = LocalDate.now(ZONE).withDayOfMonth(1);
        ProfitAndLoss month = pnl(business, monthStart, LocalDate.now(ZONE));
        long pending = expenseRepo.countByBusinessAndStatus(business, ExpenseService.PENDING);
        return new Summary(money, totalMoney, ar, ap, stock, capital, monthStart, month.totalIncome(),
                month.totalCostOfSales().add(month.totalExpenses()), month.netProfit(), pending,
                invoicesMissing(business).size());
    }

    // ── Catching up old transactions ─────────────────────────────────────────

    /**
     * Posts the books for sales and purchases made before the company had accounts (their entries
     * were skipped then). Invoices with returns or voids are left alone and listed, since rebuilding
     * those needs a person to check them.
     */
    @Transactional
    public CatchUp catchUp(Long businessId, String email) {
        accessHelper.requireCapability(businessId, email, Capability.MANAGE_ACCOUNTS);
        Business business = accessHelper.requireBusiness(businessId);
        accountService.ensureDefaults(business, email);
        int invoices = 0, payments = 0, skipped = 0;
        List<String> notes = new ArrayList<>();
        for (Invoice inv : invoicesMissing(business)) {
            boolean hasReturns = inv.getReturnedAmount() != null && inv.getReturnedAmount().signum() > 0;
            if (inv.getInvoiceStatus() == InvoiceStatus.VOID || hasReturns) {
                skipped++;
                notes.add("#" + inv.getInvoiceNumber() + (hasReturns ? " has returns" : " was voided") + " — check it by hand");
                continue;
            }
            LocalDateTime when = toLocal(inv.getCreatedAt());
            boolean sale = inv.getType() == InvoiceType.SALES;
            List<EntryLine> lines = new ArrayList<>();
            BigDecimal total = money(inv.getTotalAmount()), tax = money(inv.getTaxAmount());
            boolean credit = inv.getPaymentMethod() == PaymentMethod.CREDIT;
            if (sale) {
                BigDecimal cogs = BigDecimal.ZERO;
                for (InvoiceDetail d : detailRepo.findByInvoice(inv)) {
                    if (d.getCostPrice() != null && d.getQuantity() != null) {
                        cogs = cogs.add(d.getCostPrice().multiply(BigDecimal.valueOf(d.getQuantity())));
                    }
                }
                cogs = cogs.setScale(2, RoundingMode.HALF_UP);
                lines.add(new EntryLine(credit ? AccountPurpose.ACCOUNTS_RECEIVABLE : JournalService.cashAccount(inv.getPaymentMethod()),
                        total, BigDecimal.ZERO, "Sale revenue"));
                lines.add(new EntryLine(AccountPurpose.SALES_REVENUE, BigDecimal.ZERO, total.subtract(tax), "Sales revenue"));
                if (tax.signum() > 0) lines.add(new EntryLine(AccountPurpose.TAX_PAYABLE, BigDecimal.ZERO, tax, "Tax on sale"));
                if (cogs.signum() > 0) {
                    lines.add(new EntryLine(AccountPurpose.COST_OF_GOODS_SOLD, cogs, BigDecimal.ZERO, "COGS"));
                    lines.add(new EntryLine(AccountPurpose.INVENTORY, BigDecimal.ZERO, cogs, "Inventory reduction"));
                }
            } else {
                lines.add(new EntryLine(AccountPurpose.INVENTORY, total, BigDecimal.ZERO, "Purchase inventory"));
                lines.add(new EntryLine(credit ? AccountPurpose.ACCOUNTS_PAYABLE : JournalService.cashAccount(inv.getPaymentMethod()),
                        BigDecimal.ZERO, total, "Purchase payment/liability"));
            }
            JournalTransactionType type = sale ? (credit ? JournalTransactionType.SALE_CREDIT : JournalTransactionType.SALE_CASH)
                    : (credit ? JournalTransactionType.PURCHASE_CREDIT : JournalTransactionType.PURCHASE_CASH);
            if (journalService.post(new Source(business, inv.getShop(), type, inv.getInvoiceNumber(),
                    (sale ? "Sale" : "Purchase") + " invoice #" + inv.getInvoiceNumber() + " (posted later)",
                    inv.getId(), when), lines, email) != null) invoices++;

            // Payments taken later on credit invoices.
            for (Payment p : paymentRepo.findByInvoice(inv)) {
                BigDecimal amt = money(p.getAmount());
                if (amt.signum() <= 0) continue;
                AccountPurpose cash = JournalService.cashAccount(p.getPaymentMethod());
                List<EntryLine> pl = sale
                        ? List.of(new EntryLine(cash, amt, BigDecimal.ZERO, "Customer payment received"),
                                  new EntryLine(AccountPurpose.ACCOUNTS_RECEIVABLE, BigDecimal.ZERO, amt, "AR cleared"))
                        : List.of(new EntryLine(AccountPurpose.ACCOUNTS_PAYABLE, amt, BigDecimal.ZERO, "AP cleared"),
                                  new EntryLine(cash, BigDecimal.ZERO, amt, "Supplier payment made"));
                if (journalService.post(new Source(business, inv.getShop(),
                        sale ? JournalTransactionType.CUSTOMER_PAYMENT : JournalTransactionType.SUPPLIER_PAYMENT,
                        inv.getInvoiceNumber(), "Payment on invoice #" + inv.getInvoiceNumber() + " (posted later)",
                        inv.getId(), p.getPaidAt() != null ? toLocal(p.getPaidAt()) : when), pl, email) != null) payments++;
            }
        }
        log.info("Catch-up for business {} by {}: {} invoices, {} payments posted, {} skipped",
                businessId, email, invoices, payments, skipped);
        return new CatchUp(invoices, payments, skipped, notes);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static final EnumSet<JournalTransactionType> INVOICE_TYPES = EnumSet.of(
            JournalTransactionType.SALE_CASH, JournalTransactionType.SALE_CREDIT,
            JournalTransactionType.PURCHASE_CASH, JournalTransactionType.PURCHASE_CREDIT);

    /** Invoices with no sale/purchase entry in the books. */
    private List<Invoice> invoicesMissing(Business business) {
        List<Invoice> all = invoiceRepo.findByBusinessOrderByIdAsc(business);
        if (all.isEmpty()) return List.of();
        Set<Long> booked = new HashSet<>();
        for (Journal j : journalRepo.findByBusinessAndSourceIdIn(business, all.stream().map(Invoice::getId).toList())) {
            if (INVOICE_TYPES.contains(j.getTransactionType())) booked.add(j.getSourceId());
        }
        return all.stream().filter(i -> !booked.contains(i.getId())).toList();
    }

    private ProfitAndLoss pnl(Business business, LocalDate f, LocalDate t) {
        Map<Long, BigDecimal[]> totals = totals(business, start(f), end(t));
        List<ReportRow> income = new ArrayList<>(), cos = new ArrayList<>(), exp = new ArrayList<>();
        BigDecimal tI = BigDecimal.ZERO, tC = BigDecimal.ZERO, tX = BigDecimal.ZERO;
        for (Account a : accountService.listAccountsOf(business)) {
            BigDecimal[] tot = totals.get(a.getId());
            if (tot == null) continue;
            BigDecimal bal = signed(a, tot[0], tot[1]);
            if (bal.signum() == 0) continue;
            if (a.getAccountType() == AccountType.REVENUE) {
                income.add(new ReportRow(AccountRef.of(a), bal));
                tI = tI.add(bal);
            } else if (a.getAccountType() == AccountType.EXPENSE) {
                AccountPurpose p = a.getAccountPurpose();
                boolean costOfSales = p == AccountPurpose.COST_OF_GOODS_SOLD || p == AccountPurpose.PURCHASE
                        || p == AccountPurpose.DISCOUNT_ALLOWED || p == AccountPurpose.STOCK_ADJUSTMENT;
                if (costOfSales) { cos.add(new ReportRow(AccountRef.of(a), bal)); tC = tC.add(bal); }
                else { exp.add(new ReportRow(AccountRef.of(a), bal)); tX = tX.add(bal); }
            }
        }
        BigDecimal gross = tI.subtract(tC);
        return new ProfitAndLoss(f, t, income, tI, cos, tC, gross, exp, tX, gross.subtract(tX));
    }

    /** accountId → [debit, credit] for posted journals dated in [from, to). */
    private Map<Long, BigDecimal[]> totals(Business business, LocalDateTime from, LocalDateTime to) {
        Map<Long, BigDecimal[]> out = new HashMap<>();
        for (Object[] r : entryRepo.totalsByAccount(business, from, to)) {
            out.put((Long) r[0], new BigDecimal[]{(BigDecimal) r[1], (BigDecimal) r[2]});
        }
        return out;
    }

    /** Balance in the account's normal direction: debit − credit for assets and expenses, credit − debit otherwise. */
    static BigDecimal signed(Account a, BigDecimal debit, BigDecimal credit) {
        BigDecimal d = debit != null ? debit : BigDecimal.ZERO, c = credit != null ? credit : BigDecimal.ZERO;
        boolean debitNormal = a.getAccountType() == AccountType.ASSET || a.getAccountType() == AccountType.EXPENSE;
        return debitNormal ? d.subtract(c) : c.subtract(d);
    }

    /** Current balance of one account (all posted entries). */
    public BigDecimal balanceOf(Account a) {
        BigDecimal[] t = entryRepo.totalsBeforeOf(a, END);
        return signed(a, t[0], t[1]);
    }

    private void requireEnough(Business business, Account money, BigDecimal amount) {
        // Bank accounts can go overdrawn; cash, MoMo and petty cash can't hand out more than they hold.
        if (money.getAccountPurpose() == AccountPurpose.BANK) return;
        BigDecimal bal = balanceOf(money);
        if (bal.compareTo(amount) < 0) {
            throw bad(money.getAccountName() + " has only " + bal.setScale(2, RoundingMode.HALF_UP)
                    + "; it can't pay out " + amount.setScale(2, RoundingMode.HALF_UP));
        }
    }

    private Account moneyAccount(Business business, Long accountId, PaymentMethod method) {
        if (accountId != null) return requireMoney(business, accountId);
        return accountService.findRequiredAccount(business, JournalService.cashAccount(method != null ? method : PaymentMethod.CASH));
    }

    private Account requireMoney(Business business, Long accountId) {
        Account a = accountService.requireAccount(business, accountId);
        if (a.getAccountPurpose() == null || !a.getAccountPurpose().isMoney()) {
            throw bad("'" + a.getAccountName() + "' doesn't hold money; choose cash, bank, mobile money or petty cash");
        }
        if (!Boolean.TRUE.equals(a.getActive())) throw bad("'" + a.getAccountName() + "' is switched off");
        return a;
    }

    private Journal post(Business business, JournalTransactionType type, String refPrefix, String description,
                         LocalDate date, List<EntryLine> lines, String email) {
        return post(business, type, refPrefix, description, date, lines, email, null);
    }

    private Journal post(Business business, JournalTransactionType type, String refPrefix, String description,
                         LocalDate date, List<EntryLine> lines, String email, String reference) {
        LocalDate d = date != null ? date : LocalDate.now(ZONE);
        if (d.isAfter(LocalDate.now(ZONE))) throw bad("The date can't be in the future");
        // Today's entries keep the time; back-dated ones sit at noon of that day.
        LocalDateTime when = d.equals(LocalDate.now(ZONE)) ? LocalDateTime.now(ZONE) : d.atTime(12, 0);
        String ref = reference != null ? reference
                : refPrefix + "-" + Long.toString(System.currentTimeMillis(), 36).toUpperCase();
        Journal j = journalService.post(new Source(business, null, type, ref, description, null, when), lines, email);
        if (j == null) throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "The entry could not be posted");
        return j;
    }

    private JournalView view(Journal j) {
        return view(j, entryRepo.findByJournal(j));
    }

    private static JournalView view(Journal j, List<JournalEntry> entries) {
        BigDecimal total = BigDecimal.ZERO;
        List<JournalLineView> lines = new ArrayList<>();
        for (JournalEntry e : entries) {
            total = total.add(e.getDebit());
            Account a = e.getAccount();
            lines.add(new JournalLineView(a.getId(), a.getAccountCode(), a.getAccountName(), e.getDebit(), e.getCredit(),
                    e.getDescription()));
        }
        return new JournalView(j.getId(), j.getTransactionDate(), j.getReference(), j.getDescription(),
                j.getTransactionType(), j.getShop() != null ? j.getShop().getShopName() : null, j.getCreatedBy(),
                total, lines);
    }

    private static LocalDateTime start(LocalDate d) { return d != null ? d.atStartOfDay() : BEGINNING; }
    private static LocalDateTime end(LocalDate d) { return d != null ? d.plusDays(1).atStartOfDay() : END; }
    private static LocalDateTime toLocal(Date d) {
        return d != null ? LocalDateTime.ofInstant(d.toInstant(), ZONE) : LocalDateTime.now(ZONE);
    }
    private static BigDecimal money(BigDecimal v) {
        return v != null ? v.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
    }
    private static BigDecimal positive(BigDecimal v) {
        if (v == null || v.signum() <= 0) throw bad("Enter an amount above zero");
        return v.setScale(2, RoundingMode.HALF_UP);
    }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static AppException bad(String m) { return new AppException(HttpStatus.BAD_REQUEST, m); }
}

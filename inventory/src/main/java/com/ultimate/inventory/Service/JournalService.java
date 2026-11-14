package com.ultimate.inventory.Service;

import com.ultimate.inventory.Constants.*;
import com.ultimate.inventory.Model.Accounts.Account;
import com.ultimate.inventory.Model.Accounts.Journal;
import com.ultimate.inventory.Model.Accounts.JournalEntry;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.Invoice;
import com.ultimate.inventory.Model.Payment;
import com.ultimate.inventory.Repository.Accounts.JournalEntryRepository;
import com.ultimate.inventory.Repository.Accounts.JournalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Service
public class JournalService {

    private static final Logger log = LoggerFactory.getLogger(JournalService.class);

    private final JournalRepository journalRepo;
    private final JournalEntryRepository entryRepo;
    private final AccountService accountService;

    public JournalService(JournalRepository journalRepo, JournalEntryRepository entryRepo,
                          AccountService accountService) {
        this.journalRepo = journalRepo;
        this.entryRepo = entryRepo;
        this.accountService = accountService;
    }

    /** One side of an entry: to the account with a given purpose, or (when {@code account} is set) to that account. */
    public record EntryLine(AccountPurpose purpose, Account account, BigDecimal debit, BigDecimal credit, String description) {
        public EntryLine(AccountPurpose purpose, BigDecimal debit, BigDecimal credit, String description) {
            this(purpose, null, debit, credit, description);
        }
        public static EntryLine debit(Account account, BigDecimal amount, String description) {
            return new EntryLine(account.getAccountPurpose(), account, amount, BigDecimal.ZERO, description);
        }
        public static EntryLine credit(Account account, BigDecimal amount, String description) {
            return new EntryLine(account.getAccountPurpose(), account, BigDecimal.ZERO, amount, description);
        }
    }

    /** Where a journal came from, for entries that don't belong to an invoice. */
    public record Source(Business business, com.ultimate.inventory.Model.Shop shop, JournalTransactionType type,
                         String reference, String description, Long sourceId, LocalDateTime when) {}

    @Transactional
    public Journal postSaleJournal(Invoice invoice, BigDecimal totalCogs, String performedBy) {
        Business business = invoice.getBusiness();
        boolean isCash = invoice.getPaymentMethod() != PaymentMethod.CREDIT;

        JournalTransactionType txType = isCash
                ? JournalTransactionType.SALE_CASH
                : JournalTransactionType.SALE_CREDIT;

        BigDecimal net = invoice.getTotalAmount().subtract(invoice.getTaxAmount());
        BigDecimal tax = invoice.getTaxAmount();

        AccountPurpose debitPurpose = isCash ? cashAccount(invoice.getPaymentMethod()) : AccountPurpose.ACCOUNTS_RECEIVABLE;

        List<EntryLine> lines = new java.util.ArrayList<>();
        lines.add(new EntryLine(debitPurpose, invoice.getTotalAmount(), BigDecimal.ZERO, "Sale revenue"));
        lines.add(new EntryLine(AccountPurpose.SALES_REVENUE, BigDecimal.ZERO, net, "Sales revenue"));
        if (tax.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(new EntryLine(AccountPurpose.TAX_PAYABLE, BigDecimal.ZERO, tax, "Tax on sale"));
        }
        if (totalCogs != null && totalCogs.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(new EntryLine(AccountPurpose.COST_OF_GOODS_SOLD, totalCogs, BigDecimal.ZERO, "COGS"));
            lines.add(new EntryLine(AccountPurpose.INVENTORY, BigDecimal.ZERO, totalCogs, "Inventory reduction"));
        }

        return post(business, invoice, txType,
                "Sale invoice #" + invoice.getInvoiceNumber(), lines, performedBy);
    }

    @Transactional
    public Journal postPurchaseJournal(Invoice invoice, String performedBy) {
        Business business = invoice.getBusiness();
        boolean isCash = invoice.getPaymentMethod() != PaymentMethod.CREDIT;

        JournalTransactionType txType = isCash
                ? JournalTransactionType.PURCHASE_CASH
                : JournalTransactionType.PURCHASE_CREDIT;

        AccountPurpose creditPurpose = isCash ? cashAccount(invoice.getPaymentMethod()) : AccountPurpose.ACCOUNTS_PAYABLE;

        List<EntryLine> lines = List.of(
            new EntryLine(AccountPurpose.INVENTORY, invoice.getTotalAmount(), BigDecimal.ZERO, "Purchase inventory"),
            new EntryLine(creditPurpose, BigDecimal.ZERO, invoice.getTotalAmount(), "Purchase payment/liability")
        );

        return post(business, invoice, txType,
                "Purchase invoice #" + invoice.getInvoiceNumber(), lines, performedBy);
    }

    @Transactional
    public Journal postPaymentJournal(Payment payment, String performedBy) {
        Invoice invoice = payment.getInvoice();
        Business business = invoice.getBusiness();

        boolean isSale = invoice.getType() == InvoiceType.SALES;
        JournalTransactionType txType = isSale
                ? JournalTransactionType.CUSTOMER_PAYMENT
                : JournalTransactionType.SUPPLIER_PAYMENT;

        AccountPurpose cashPurpose = cashAccount(payment.getPaymentMethod());

        List<EntryLine> lines = isSale
                ? List.of(
                    new EntryLine(cashPurpose, payment.getAmount(), BigDecimal.ZERO, "Customer payment received"),
                    new EntryLine(AccountPurpose.ACCOUNTS_RECEIVABLE, BigDecimal.ZERO, payment.getAmount(), "AR cleared"))
                : List.of(
                    new EntryLine(AccountPurpose.ACCOUNTS_PAYABLE, payment.getAmount(), BigDecimal.ZERO, "AP cleared"),
                    new EntryLine(cashPurpose, BigDecimal.ZERO, payment.getAmount(), "Supplier payment made"));

        return post(business, invoice, txType,
                "Payment on invoice #" + invoice.getInvoiceNumber(), lines, performedBy);
    }

    /** Goods a customer returned: revenue and tax come back out, the goods go back into stock. */
    @Transactional
    public Journal postSaleReturnJournal(Invoice invoice, BigDecimal value, BigDecimal tax, BigDecimal cost,
                                         String reference, String performedBy) {
        List<EntryLine> lines = new java.util.ArrayList<>();
        lines.add(new EntryLine(AccountPurpose.SALES_REVENUE, value.subtract(tax), BigDecimal.ZERO, "Sales return"));
        if (tax.signum() > 0) lines.add(new EntryLine(AccountPurpose.TAX_PAYABLE, tax, BigDecimal.ZERO, "Tax on return"));
        lines.add(new EntryLine(AccountPurpose.ACCOUNTS_RECEIVABLE, BigDecimal.ZERO, value, "Credit note"));
        if (cost != null && cost.signum() > 0) {
            lines.add(new EntryLine(AccountPurpose.INVENTORY, cost, BigDecimal.ZERO, "Returned stock"));
            lines.add(new EntryLine(AccountPurpose.COST_OF_GOODS_SOLD, BigDecimal.ZERO, cost, "COGS reversed"));
        }
        return post(invoice.getBusiness(), invoice, JournalTransactionType.SALE_RETURN,
                "Return " + reference + " on sale #" + invoice.getInvoiceNumber(), lines, performedBy);
    }

    /** Goods sent back to the supplier: what we owe them and our stock both go down. */
    @Transactional
    public Journal postPurchaseReturnJournal(Invoice invoice, BigDecimal value, String reference, String performedBy) {
        List<EntryLine> lines = List.of(
            new EntryLine(AccountPurpose.ACCOUNTS_PAYABLE, value, BigDecimal.ZERO, "Debit note"),
            new EntryLine(AccountPurpose.INVENTORY, BigDecimal.ZERO, value, "Stock returned to supplier"));
        return post(invoice.getBusiness(), invoice, JournalTransactionType.PURCHASE_RETURN,
                "Return " + reference + " on purchase #" + invoice.getInvoiceNumber(), lines, performedBy);
    }

    /** Money handed back: to a customer (sale) or received from a supplier (purchase). */
    @Transactional
    public Journal postRefundJournal(Invoice invoice, BigDecimal amount, PaymentMethod method, String performedBy) {
        boolean isSale = invoice.getType() == InvoiceType.SALES;
        AccountPurpose cash = cashAccount(method);
        List<EntryLine> lines = isSale
                ? List.of(new EntryLine(AccountPurpose.ACCOUNTS_RECEIVABLE, amount, BigDecimal.ZERO, "Refund due cleared"),
                          new EntryLine(cash, BigDecimal.ZERO, amount, "Refund paid to customer"))
                : List.of(new EntryLine(cash, amount, BigDecimal.ZERO, "Refund from supplier"),
                          new EntryLine(AccountPurpose.ACCOUNTS_PAYABLE, BigDecimal.ZERO, amount, "Supplier refund cleared"));
        return post(invoice.getBusiness(), invoice,
                isSale ? JournalTransactionType.CUSTOMER_PAYMENT : JournalTransactionType.SUPPLIER_PAYMENT,
                "Refund on invoice #" + invoice.getInvoiceNumber(), lines, performedBy);
    }

    /**
     * Cancels everything an invoice put in the books (sale/purchase, payments, returns, refunds)
     * by posting a mirror entry for each journal. The originals stay for the audit trail.
     */
    @Transactional
    public int reverseInvoiceJournals(Invoice invoice, String performedBy) {
        int n = 0;
        for (Journal original : journalRepo.findByBusinessAndReferenceAndStatus(
                invoice.getBusiness(), invoice.getInvoiceNumber(), JournalStatus.POSTED)) {
            List<EntryLine> lines = new java.util.ArrayList<>();
            for (JournalEntry e : entryRepo.findByJournal(original)) {
                // Same account as the original (not just the same purpose), debits and credits swapped.
                lines.add(new EntryLine(e.getAccount().getAccountPurpose(), e.getAccount(), e.getCredit(), e.getDebit(),
                        "Reversal: " + (e.getDescription() != null ? e.getDescription() : "")));
            }
            Journal rev = postWithReference(invoice.getBusiness(), invoice, original.getTransactionType(),
                    "Void: reversal of " + original.getDescription(), lines, performedBy,
                    InventoryService.voidRef(invoice));
            if (rev != null) n++;
        }
        return n;
    }

    private Journal post(Business business, Invoice invoice, JournalTransactionType txType,
                         String description, List<EntryLine> lines, String performedBy) {
        return postWithReference(business, invoice, txType, description, lines, performedBy, invoice.getInvoiceNumber());
    }

    private Journal postWithReference(Business business, Invoice invoice, JournalTransactionType txType,
                                      String description, List<EntryLine> lines, String performedBy,
                                      String reference) {
        return post(new Source(business, invoice.getShop(), txType, reference, description, invoice.getId(),
                LocalDateTime.now()), lines, performedBy);
    }

    // ── Entries that don't come from an invoice ──────────────────────────────

    /**
     * Stock written off or found (damage, theft, expiry, counts, stock in without a purchase).
     * {@code value} is the cost of the change: negative for stock lost, positive for stock found.
     */
    @Transactional
    public Journal postStockAdjustmentJournal(Business business, com.ultimate.inventory.Model.Shop shop,
                                              BigDecimal value, String description, String performedBy) {
        return postStockValueJournal(business, shop, value, AccountPurpose.STOCK_ADJUSTMENT,
                JournalTransactionType.STOCK_ADJUSTMENT, description, performedBy);
    }

    /**
     * Stock received without a supplier purchase (usually goods the business already had when it
     * started using the system): the owner brought it in, so it counts as capital.
     */
    @Transactional
    public Journal postStockInJournal(Business business, com.ultimate.inventory.Model.Shop shop,
                                      BigDecimal value, String description, String performedBy) {
        return postStockValueJournal(business, shop, value, AccountPurpose.CAPITAL,
                JournalTransactionType.OPENING_BALANCE, description, performedBy);
    }

    private Journal postStockValueJournal(Business business, com.ultimate.inventory.Model.Shop shop, BigDecimal value,
                                          AccountPurpose other, JournalTransactionType type,
                                          String description, String performedBy) {
        if (value == null) return null;
        BigDecimal v = value.abs().setScale(2, java.math.RoundingMode.HALF_UP);
        if (v.signum() == 0) return null;
        List<EntryLine> lines = value.signum() < 0
                ? List.of(new EntryLine(other, v, BigDecimal.ZERO, "Stock written off"),
                          new EntryLine(AccountPurpose.INVENTORY, BigDecimal.ZERO, v, "Stock reduced"))
                : List.of(new EntryLine(AccountPurpose.INVENTORY, v, BigDecimal.ZERO, "Stock added"),
                          new EntryLine(other, BigDecimal.ZERO, v,
                                  other == AccountPurpose.CAPITAL ? "Stock brought in by the owner" : "Stock gain"));
        String ref = (type == JournalTransactionType.OPENING_BALANCE ? "STKIN-" : "STKADJ-")
                + Long.toString(System.currentTimeMillis(), 36).toUpperCase();
        return post(new Source(business, shop, type, ref, description, null, LocalDateTime.now()), lines, performedBy);
    }

    /**
     * Posts a balanced journal. Lines name an account or a purpose; a purpose with no account gets the
     * standard chart set up first. If an account is still missing nothing is posted and nothing is
     * thrown, so the sale/purchase itself still goes through.
     */
    @Transactional
    public Journal post(Source src, List<EntryLine> lines, String performedBy) {
        Business business = src.business();
        BigDecimal dr = BigDecimal.ZERO, cr = BigDecimal.ZERO;
        for (EntryLine l : lines) {
            dr = dr.add(nz(l.debit()));
            cr = cr.add(nz(l.credit()));
        }
        if (dr.compareTo(cr) != 0) {
            // Never block the sale/purchase over a bookkeeping bug; leave a loud trace instead.
            log.error("Journal NOT posted for {}: debits {} ≠ credits {}", src.reference(), dr, cr);
            return null;
        }
        if (dr.signum() == 0) return null; // nothing to record

        java.util.Map<AccountPurpose, Account> byPurpose = new java.util.EnumMap<>(AccountPurpose.class);
        boolean setUp = false;
        for (EntryLine line : lines) {
            if (line.account() != null || line.purpose() == null || byPurpose.containsKey(line.purpose())) continue;
            java.util.Optional<Account> acc = accountService.findAccount(business, line.purpose());
            if (acc.isEmpty() && !setUp) {
                accountService.ensureDefaults(business, "system");
                setUp = true;
                acc = accountService.findAccount(business, line.purpose());
            }
            if (acc.isEmpty()) {
                log.warn("Journal skipped for {}: no {} account set up", src.reference(), line.purpose());
                return null;
            }
            byPurpose.put(line.purpose(), acc.get());
        }

        Journal journal = new Journal();
        journal.setBusiness(business);
        journal.setShop(src.shop());
        journal.setTransactionDate(src.when() != null ? src.when() : LocalDateTime.now());
        journal.setReference(src.reference());
        journal.setDescription(src.description());
        journal.setTransactionType(src.type());
        journal.setSourceId(src.sourceId());
        journal.setStatus(JournalStatus.POSTED);
        journal.setCreatedBy(performedBy);
        journal.setCreatedAt(new Date());
        Journal saved = journalRepo.save(journal);

        for (EntryLine line : lines) {
            if (nz(line.debit()).signum() == 0 && nz(line.credit()).signum() == 0) continue;
            JournalEntry entry = new JournalEntry();
            entry.setJournal(saved);
            entry.setAccount(line.account() != null ? line.account() : byPurpose.get(line.purpose()));
            entry.setDebit(nz(line.debit()));
            entry.setCredit(nz(line.credit()));
            entry.setDescription(line.description());
            entry.setCreatedBy(performedBy);
            entry.setCreatedAt(new Date());
            entryRepo.save(entry);
        }

        log.info("Journal posted: ref={} type={} entries={}", saved.getReference(), src.type(), lines.size());
        return saved;
    }

    /** The money account a payment method lands in. */
    public static AccountPurpose cashAccount(PaymentMethod method) {
        if (method == null) return AccountPurpose.CASH;
        return switch (method) {
            case MOBILE_MONEY -> AccountPurpose.MOBILE_MONEY;
            case BANK_TRANSFER, PAYSTACK, EMERGENT -> AccountPurpose.BANK;
            default -> AccountPurpose.CASH;
        };
    }

    private static BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
}

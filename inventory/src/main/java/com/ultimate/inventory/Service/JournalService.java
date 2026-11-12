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

    record EntryLine(AccountPurpose purpose, BigDecimal debit, BigDecimal credit, String description) {}

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
                lines.add(new EntryLine(e.getAccount().getAccountPurpose(), e.getCredit(), e.getDebit(),
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

    /**
     * Posts a balanced journal. If the business has not set up an account the entry needs, nothing
     * is posted (and nothing is thrown, so the sale/purchase itself still goes through).
     */
    private Journal postWithReference(Business business, Invoice invoice, JournalTransactionType txType,
                                      String description, List<EntryLine> lines, String performedBy,
                                      String reference) {
        java.util.Map<AccountPurpose, Account> accounts = new java.util.EnumMap<>(AccountPurpose.class);
        for (EntryLine line : lines) {
            if (line.purpose() == null) continue;
            java.util.Optional<Account> acc = accountService.findAccount(business, line.purpose());
            if (acc.isEmpty()) {
                log.warn("Journal skipped for {}: no {} account set up", reference, line.purpose());
                return null;
            }
            accounts.put(line.purpose(), acc.get());
        }
        Journal journal = new Journal();
        journal.setBusiness(business);
        journal.setShop(invoice.getShop());
        journal.setTransactionDate(LocalDateTime.now());
        journal.setReference(reference);
        journal.setDescription(description);
        journal.setTransactionType(txType);
        journal.setSourceId(invoice.getId());
        journal.setStatus(JournalStatus.POSTED);
        journal.setCreatedBy(performedBy);
        journal.setCreatedAt(new Date());
        Journal saved = journalRepo.save(journal);

        for (EntryLine line : lines) {
            Account account = accounts.get(line.purpose());
            JournalEntry entry = new JournalEntry();
            entry.setJournal(saved);
            entry.setAccount(account);
            entry.setDebit(line.debit());
            entry.setCredit(line.credit());
            entry.setDescription(line.description());
            entry.setCreatedBy(performedBy);
            entry.setCreatedAt(new Date());
            entryRepo.save(entry);
        }

        log.info("Journal posted: ref={} type={} entries={}", saved.getReference(), txType, lines.size());
        return saved;
    }

    private AccountPurpose cashAccount(PaymentMethod method) {
        return switch (method) {
            case BANK_TRANSFER, MOBILE_MONEY, PAYSTACK, EMERGENT -> AccountPurpose.BANK;
            default -> AccountPurpose.CASH;
        };
    }
}

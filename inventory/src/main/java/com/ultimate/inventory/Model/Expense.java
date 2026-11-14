package com.ultimate.inventory.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ultimate.inventory.Model.Accounts.Account;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * Money spent running the business (electricity, water, transport…), usually from petty cash.
 * A cashier's expense waits for a manager (status PENDING) and reaches the books only when approved;
 * a manager's or admin's is approved at once.
 */
@Getter
@Setter
@Entity
@Table(name = "expense", indexes = @Index(name = "idx_expense_business_status", columnList = "business_id, status"))
public class Expense extends Model {

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    /** The branch/location it was spent for (optional: some costs are company-wide). */
    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "shop_id")
    private Shop shop;

    /** What it was for: an expense account such as Electricity. */
    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "expense_account_id", nullable = false)
    private Account expenseAccount;

    /** Where the money came from: Petty Cash, Cash in Hand, Bank or Mobile Money. */
    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "paid_from_account_id", nullable = false)
    private Account paidFrom;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    /** Who was paid (ECG, Ghana Water, a plumber…). */
    @Column(name = "payee", length = 120)
    private String payee;

    /** Receipt, bill or MoMo transaction number. */
    @Column(name = "receipt_number", length = 80)
    private String receiptNumber;

    /** PENDING, APPROVED, REJECTED, CANCELLED or VOID (stored as text). */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "reference", length = 30)
    private String reference;

    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    @Column(name = "decided_by")
    private String decidedBy;

    @Column(name = "decided_at")
    private Date decidedAt;

    @Column(name = "decision_note", length = 255)
    private String decisionNote;

    @Column(name = "journal_id")
    private Long journalId;
}

package com.ultimate.inventory.Model.Accounts;

import com.ultimate.inventory.Model.Model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "journal_entry")
public class JournalEntry extends Model {

    @ManyToOne
    @JoinColumn(name = "journal_id", nullable = false)
    private Journal journal;

    @ManyToOne
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal debit = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal credit = BigDecimal.ZERO;

    @Column(name = "description")
    private String description;
}

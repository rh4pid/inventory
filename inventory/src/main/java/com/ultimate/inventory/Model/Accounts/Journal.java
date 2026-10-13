package com.ultimate.inventory.Model.Accounts;

import com.ultimate.inventory.Constants.JournalStatus;
import com.ultimate.inventory.Constants.JournalTransactionType;
import com.ultimate.inventory.Model.Model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "journal")
public class Journal extends Model {

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "shop_id")
    private Long shopId;

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    @Column(name = "reference", nullable = false)
    private String reference;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private JournalTransactionType transactionType;

    @Column(name = "source_id")
    private Long sourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JournalStatus status;

}
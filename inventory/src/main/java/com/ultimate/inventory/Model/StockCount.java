package com.ultimate.inventory.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ultimate.inventory.Constants.StockCountStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/** A stocktake of one location. Lines hold the system quantity at the start and what was counted. */
@Getter
@Setter
@Entity
@Table(name = "stock_count")
public class StockCount extends Model {

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    /** e.g. "CNT-00012"; used as the reference on the adjustments it posts. */
    @Column(name = "reference", length = 30)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockCountStatus status = StockCountStatus.OPEN;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "submitted_by")
    private String submittedBy;
    @Column(name = "submitted_at")
    private Date submittedAt;
    @Column(name = "posted_by")
    private String postedBy;
    @Column(name = "posted_at")
    private Date postedAt;

    public Long getShopId() { return shop != null ? shop.getId() : null; }
    public String getShopName() { return shop != null ? shop.getShopName() : null; }
}

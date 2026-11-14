package com.ultimate.inventory.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ultimate.inventory.Constants.StockMovementType;
import com.ultimate.inventory.Constants.StockRequestStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Date;

/**
 * A stock-out (write-off) or adjustment made by someone who needs a manager's approval.
 * Nothing changes in stock until it is approved.
 */
@Getter
@Setter
@Entity
@Table(name = "stock_request", indexes = @Index(name = "idx_request_business_status", columnList = "business_id, status"))
public class StockRequest extends Model {

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** STOCK_OUT or ADJUSTMENT. */
    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 20)
    private StockMovementType requestType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockRequestStatus status = StockRequestStatus.PENDING;

    // ── Stock out ──
    /** Quantity as entered, in {@link #unitId} (null = base unit). */
    private Double quantity;
    @Column(name = "unit_id")
    private Long unitId;
    /** Human-readable form of what was asked, e.g. "2 × Carton (24 Piece)" or "40 Piece". */
    @Column(name = "summary")
    private String summary;

    // ── Adjustment ──
    /** Counted quantity in base units (whole location, or one batch when batchId is set). */
    @Column(name = "counted_quantity")
    private Double countedQuantity;
    /** What the system showed when the request was made; approval applies counted − expected. */
    @Column(name = "expected_quantity")
    private Double expectedQuantity;
    @Column(name = "batch_number", length = 80)
    private String batchNumber;
    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    // ── Both ──
    @Column(name = "batch_id")
    private Long batchId;
    private String reason;
    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "requested_by", nullable = false)
    private String requestedBy;
    @Column(name = "decided_by")
    private String decidedBy;
    @Column(name = "decided_at")
    private Date decidedAt;
    @Column(name = "decision_note")
    private String decisionNote;

    public Long getShopId() { return shop != null ? shop.getId() : null; }
    public String getShopName() { return shop != null ? shop.getShopName() : null; }
    public Long getProductId() { return product != null ? product.getId() : null; }
    public String getProductName() { return product != null ? product.getProductName() : null; }
    public String getBaseUnit() {
        return product != null && product.getUnit() != null ? product.getUnit().getName() : null;
    }
}

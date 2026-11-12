package com.ultimate.inventory.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Date;

/**
 * A lot of one product held at one location. Used when the business tracks batches.
 * Quantities are in the product's base unit. For a location, the sum of
 * {@code quantityRemaining} over its batches always equals the {@link Inventory} quantity
 * (or zero when that quantity is negative).
 */
@Getter
@Setter
@Entity
@Table(name = "stock_batch", indexes = {
        @Index(name = "idx_batch_product_shop", columnList = "product_id, shop_id"),
        @Index(name = "idx_batch_expiry", columnList = "expiry_date")
})
public class StockBatch extends Model {

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    /** Lot / batch number as printed on the goods, or a generated one. */
    @Column(name = "batch_number", nullable = false, length = 80)
    private String batchNumber;

    /** Null = does not expire. */
    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /** When the goods first entered the business (kept across transfers, used for FIFO). */
    @Column(name = "received_at", nullable = false)
    private Date receivedAt;

    /** Cost per base unit for this lot. */
    @Column(name = "unit_cost")
    private Double unitCost;

    @Column(name = "quantity_received", nullable = false)
    private Double quantityReceived;

    @Column(name = "quantity_remaining", nullable = false)
    private Double quantityRemaining;

    @Column(name = "supplier_id")
    private Long supplierId;

    /** Where the batch came from: purchase number, "Opening stock", "Transfer from …". */
    @Column(name = "source")
    private String source;

    public boolean isExpired(LocalDate today) {
        return expiryDate != null && expiryDate.isBefore(today);
    }
}

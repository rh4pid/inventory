package com.ultimate.inventory.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "stock_count_line",
       uniqueConstraints = @UniqueConstraint(columnNames = {"stock_count_id", "product_id"}))
public class StockCountLine extends Model {

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "stock_count_id", nullable = false)
    private StockCount stockCount;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** System quantity (base units) when the count started. */
    @Column(name = "system_quantity", nullable = false)
    private Double systemQuantity;

    /** Physically counted quantity (base units); null = not counted, left unchanged on posting. */
    @Column(name = "counted_quantity")
    private Double countedQuantity;

    /** Change actually applied when the count was posted. */
    @Column(name = "posted_difference")
    private Double postedDifference;

    public Long getProductId() { return product != null ? product.getId() : null; }
    public String getProductName() { return product != null ? product.getProductName() : null; }
    public String getSku() { return product != null ? product.getSku() : null; }
    public String getBaseUnit() {
        return product != null && product.getUnit() != null ? product.getUnit().getName() : null;
    }
}

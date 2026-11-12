package com.ultimate.inventory.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "invoice_detail")
public class InvoiceDetail extends Model {

    @ManyToOne
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Double quantity;

    /** Unit the line was entered in (e.g. "Carton"); null = product base unit. */
    @Column(name = "unit_name")
    private String unitName;

    /** Quantity converted to the product base unit: what actually moved in stock. */
    @Column(name = "base_quantity")
    private Double baseQuantity;

    /** Batches the line was taken from / received into, e.g. "LOT-A12 x3, LOT-B07 x2" (for recalls). */
    @Column(name = "batch_info", length = 500)
    private String batchInfo;

    /** How much of {@link #quantity} (same unit) has been returned so far. */
    @Column(name = "returned_quantity")
    private Double returnedQuantity = 0.0;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "cost_price", precision = 19, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "discount_amount", precision = 19, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", precision = 19, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_line", precision = 19, scale = 2)
    private BigDecimal totalLine;
}

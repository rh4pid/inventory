package com.ultimate.inventory.Model;

import com.ultimate.inventory.Constants.StockMovementType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "stock_movement")
public class StockMovement extends Model {

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false)
    private StockMovementType movementType;

    @Column(name = "quantity_before", nullable = false)
    private Double quantityBefore;

    @Column(name = "quantity_change", nullable = false)
    private Double quantityChange;

    @Column(name = "quantity_after", nullable = false)
    private Double quantityAfter;

    @Column(name = "reference_code")
    private String referenceCode;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "supplier_id")
    private Long supplierId;

    /** Quantity as the user entered it, before conversion to the base unit (e.g. 2). */
    @Column(name = "entered_quantity")
    private Double enteredQuantity;

    /** Unit the user entered it in (e.g. "Carton (24)"); null = base unit. */
    @Column(name = "entered_unit")
    private String enteredUnit;

    /** Batch this movement touched, when the business tracks batches. */
    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "batch_number", length = 80)
    private String batchNumber;

    /** Manager who approved this change, when it needed approval. */
    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "performed_by")
    private String performedBy;
}

package com.ultimate.inventory.DTO;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class SaleItemDTO {

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private Double quantity;

    /** Unit the quantity is entered in (a pack size of the product). Null = base unit. */
    private Long unitId;

    private BigDecimal unitPrice;

    private BigDecimal discountAmount;

    /** Sell from this batch only. Null = pick automatically (earliest expiry first, expired skipped). */
    private Long batchId;
}

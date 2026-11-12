package com.ultimate.inventory.DTO;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class PurchaseItemDTO {

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private Double quantity;

    /** Unit the quantity is entered in (a pack size of the product). Null = base unit. */
    private Long unitId;

    @NotNull(message = "Unit price is required")
    @Positive(message = "Unit price must be positive")
    private BigDecimal unitPrice;

    /** Lot number on the delivered goods. Generated when blank. Used only when batches are tracked. */
    @Size(max = 80)
    private String batchNumber;

    /** Used only when batches are tracked. */
    private LocalDate expiryDate;
}

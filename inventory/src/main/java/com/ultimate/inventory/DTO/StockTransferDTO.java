package com.ultimate.inventory.DTO;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StockTransferDTO {

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Source location is required")
    private Long fromShopId;

    @NotNull(message = "Destination location is required")
    private Long toShopId;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private Double quantity;

    /** Unit the quantity is entered in (a pack size of the product). Null = base unit. */
    private Long unitId;

    /** Move this batch only. Null = pick automatically (expired batches are skipped). */
    private Long batchId;

    private String notes;
}

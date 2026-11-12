package com.ultimate.inventory.DTO;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StockOutDTO {

    @NotNull(message = "Product ID is required")
    private Long productId;

    /** Taken from the URL path; ignored in the body. */
    private Long shopId;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private Double quantity;

    /** Unit the quantity is entered in (a pack size of the product). Null = base unit. */
    private Long unitId;

    /** Take from this batch only (e.g. write off an expired lot). Null = pick automatically. */
    private Long batchId;

    private String notes;
    private String reason;
}

package com.ultimate.inventory.DTO;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class StockInDTO {

    @NotNull(message = "Product ID is required")
    private Long productId;

    /** Taken from the URL path; ignored in the body. */
    private Long shopId;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private Double quantity;

    /** Unit the quantity is entered in (a pack size of the product). Null = base unit. */
    private Long unitId;

    private String notes;
    private Long supplierId;

    /** Cost per entered unit (per carton if unitId is Carton). Null = product cost price. */
    @Positive(message = "Cost price must be positive")
    private Double costPrice;

    /** Lot number printed on the goods. Generated when blank. Used only when batches are tracked. */
    @Size(max = 80)
    private String batchNumber;

    /** Used only when batches are tracked. */
    private LocalDate expiryDate;
}

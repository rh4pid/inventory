package com.ultimate.inventory.DTO;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StockInDTO {

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Shop ID is required")
    private Long shopId;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private Double quantity;

    private String notes;
    private Long supplierId;
    private Double costPrice;
}

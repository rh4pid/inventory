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

    @NotNull(message = "Source shop ID is required")
    private Long fromShopId;

    @NotNull(message = "Destination shop ID is required")
    private Long toShopId;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private Double quantity;

    private String notes;
}

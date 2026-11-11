package com.ultimate.inventory.DTO;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StockAdjustmentDTO {

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Shop ID is required")
    private Long shopId;

    @NotNull(message = "New quantity is required")
    @Min(value = 0, message = "Quantity cannot be negative")
    private Double newQuantity;

    private String notes;
    private String reason;
}

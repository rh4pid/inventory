package com.ultimate.inventory.DTO;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductUnitDTO {
    @NotNull(message = "Unit is required")
    private Long unitId;
    @NotNull(message = "Conversion factor is required")
    @Positive(message = "Conversion factor must be positive")
    private Double conversionFactor;
    private String barcode;
    @Positive(message = "Selling price must be positive")
    private Double sellingPrice;
}

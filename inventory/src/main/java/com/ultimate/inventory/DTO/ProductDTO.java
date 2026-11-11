package com.ultimate.inventory.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductDTO {

    @NotBlank(message = "Product name is required")
    private String productName;

    private String description;
    private String sku;
    private String barcode;

    @NotNull(message = "Cost price is required")
    @Positive(message = "Cost price must be positive")
    private Double costPrice;

    @NotNull(message = "Selling price is required")
    @Positive(message = "Selling price must be positive")
    private Double sellingPrice;

    private String discountType;
    private Double discountValue;
    private Integer reorderLevel;
    private Long categoryId;
    private Long unitId;
    private Long taxRateId;
}

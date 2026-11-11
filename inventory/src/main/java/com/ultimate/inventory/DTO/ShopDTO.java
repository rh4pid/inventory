package com.ultimate.inventory.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ShopDTO {

    @NotNull(message = "Business ID is required")
    private Integer businessId;

    @NotBlank(message = "Shop name is required")
    private String shopName;

    private String shopLocation;
    private Boolean active;
}

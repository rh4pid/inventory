package com.ultimate.inventory.DTO;

import com.ultimate.inventory.Constants.LocationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ShopDTO {

    @NotNull(message = "Business ID is required")
    private Integer businessId;

    @NotBlank(message = "Location name is required")
    private String shopName;

    private String shopLocation;

    /** Shop, warehouse, storeroom… Defaults to SHOP when not given. */
    private LocationType locationType;

    private Boolean active;
}

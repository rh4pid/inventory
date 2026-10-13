package com.ultimate.inventory.DTO;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ShopDTO {
    private Integer businessId;
    private String shopName;
    private String shopLocation;
    private Boolean active;
}


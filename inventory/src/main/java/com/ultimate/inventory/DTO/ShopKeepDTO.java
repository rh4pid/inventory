package com.ultimate.inventory.DTO;

import com.ultimate.inventory.Constants.UserRoles;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ShopKeepDTO {
    private String email;
    private String shopCode;
    private String roles;
}

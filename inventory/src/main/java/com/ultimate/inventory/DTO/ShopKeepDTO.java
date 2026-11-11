package com.ultimate.inventory.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ShopKeepDTO {

    @NotBlank(message = "User email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Shop code is required")
    private String shopCode;

    @NotBlank(message = "Role is required")
    private String roles;
}

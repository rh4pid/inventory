package com.ultimate.inventory.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerDTO {

    @NotBlank(message = "First name is required")
    private String firstName;

    private String lastName;

    @Email(message = "Email must be valid")
    private String email;

    private String phone;
    private String address;
}

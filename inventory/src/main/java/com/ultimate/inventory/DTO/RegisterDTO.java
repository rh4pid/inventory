package com.ultimate.inventory.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterDTO {

    @NotBlank(message = "First name is required")
    private String firstName;

    private String otherName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Business name is required")
    private String businessName;

    @NotBlank(message = "Business email is required")
    @Email(message = "Invalid business email format")
    private String businessEmail;

    @NotBlank(message = "Business contact is required")
    private String businessContact;

    private String businessSecondaryContact;
}

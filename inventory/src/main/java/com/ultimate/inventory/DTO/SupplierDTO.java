package com.ultimate.inventory.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SupplierDTO {

    @NotBlank(message = "Company name is required")
    private String companyName;

    private String relationshipOfficer;

    @Email(message = "Company email must be valid")
    private String companyEmail;

    @Email(message = "RM email must be valid")
    private String rmEmail;

    private String telephonePrimary;
    private String telephoneSecondary;
}

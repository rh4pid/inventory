package com.ultimate.inventory.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProfileDTO {

    @NotBlank(message = "First name is required")
    private String firstName;

    private String otherName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    private String telephonePrimary;
    private String telephoneSecondary;

    @Size(min = 3, max = 30, message = "Username must be 3-30 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username can only contain letters, numbers and underscores")
    private String username;
}

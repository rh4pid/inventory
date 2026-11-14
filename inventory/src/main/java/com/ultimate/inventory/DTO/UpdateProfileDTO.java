package com.ultimate.inventory.DTO;

import jakarta.validation.constraints.NotBlank;
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

    /** Optional; checked by Usernames (a leading @ and capitals are fine, they are tidied away). */
    private String username;
}

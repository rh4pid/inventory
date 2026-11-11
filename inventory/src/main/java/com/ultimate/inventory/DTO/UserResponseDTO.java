package com.ultimate.inventory.DTO;

import com.ultimate.inventory.Model.User;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserResponseDTO {
    private Long id;
    private String firstName;
    private String otherName;
    private String lastName;
    private String email;
    private String username;
    private String telephonePrimary;
    private String telephoneSecondary;
    private String profilePicture;

    public UserResponseDTO(User user) {
        this.id = user.getId();
        this.firstName = user.getFirstName();
        this.otherName = user.getOtherName();
        this.lastName = user.getLastName();
        this.email = user.getEmail();
        this.username = user.getUsername();
        this.telephonePrimary = user.getTelephonePrimary();
        this.telephoneSecondary = user.getTelephoneSecondary();
        this.profilePicture = user.getProfilePicture();
    }
}

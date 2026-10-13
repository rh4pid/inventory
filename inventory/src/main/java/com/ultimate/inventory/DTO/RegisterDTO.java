package com.ultimate.inventory.DTO;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Setter
@Getter
public class RegisterDTO {
    private String firstName;
    private String otherName;
    private String lastName;
    private String email;
    private String password;

    private String businessName;
    private String businessEmail;
    private String businessContact;
    private String businessSecondaryContact;
}




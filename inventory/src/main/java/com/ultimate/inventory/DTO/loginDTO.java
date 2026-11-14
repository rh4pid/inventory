package com.ultimate.inventory.DTO;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class loginDTO {
    /** Email address or username (with or without the @). Older clients send it as "email". */
    @JsonAlias("email")
    @NotBlank(message = "Enter your email or username")
    private String login;
    @NotBlank(message = "Password is required")
    private String password;
}

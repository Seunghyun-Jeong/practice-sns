package com.example.sns.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserSignUpRequest {
    @Size(min = 1, max = 30)
    @Pattern(regexp = "^(?![0-9]+$)(?!.*[.]{2})(?!^\\.)(?!.*\\.$)[a-zA-Z0-9._]{1,30}$")
    private String username;

    @Size(min = 8, max = 15)
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+]).+$")
    private String password;
}

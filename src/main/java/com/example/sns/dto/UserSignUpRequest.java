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
    @Size(min = 4, max = 10)
    @Pattern(regexp = "^(?![0-9]+$)(?!.*[_.]{2})[a-z0-9._]{4,10}$")
    private String username;

    @Size(min = 8, max = 15)
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+]).+$")
    private String password;
}

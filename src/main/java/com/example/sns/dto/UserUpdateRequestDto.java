package com.example.sns.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserUpdateRequestDto {
    @Size(min = 1, max = 30)
    @Pattern(regexp = "^(?![0-9]+$)(?!.*[.]{2})(?!^\\.)(?!.*\\.$)[a-zA-Z0-9._]{1,30}$")
    private String username;
}

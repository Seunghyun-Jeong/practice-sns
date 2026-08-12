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
    /** DB의 username 컬럼(길이 10)과 화면 검증(4~10자)에 맞춘다. */
    @Size(min = 4, max = 10, message = "아이디는 4자 이상 10자 이하여야 합니다.")
    @Pattern(
            regexp = "^(?![0-9]+$)(?!.*[_.]{2})(?!^[._])(?!.*[._]$)[a-z0-9._]+$",
            message = "아이디는 영문 소문자, 숫자, '.', '_'만 사용할 수 있으며, 숫자로만 이루어지거나 '.', '_'가 연속되거나 맨 앞·뒤에 올 수 없습니다."
    )
    private String username;

    @Size(min = 8, max = 15, message = "비밀번호는 8자 이상 15자 이하여야 합니다.")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+]).+$",
            message = "비밀번호는 영문 대문자, 소문자, 숫자, 특수문자를 모두 포함해야 합니다."
    )
    private String password;
}

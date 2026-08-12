package com.example.sns.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserUpdateRequestDto {
    /** 회원가입과 동일한 규칙 (DB의 username 컬럼 길이 10과 맞춘다) */
    @Size(min = 4, max = 10, message = "아이디는 4자 이상 10자 이하여야 합니다.")
    @Pattern(
            regexp = "^(?![0-9]+$)(?!.*[_.]{2})(?!^[._])(?!.*[._]$)[a-z0-9._]+$",
            message = "아이디는 영문 소문자, 숫자, '.', '_'만 사용할 수 있으며, 숫자로만 이루어지거나 '.', '_'가 연속되거나 맨 앞·뒤에 올 수 없습니다."
    )
    private String username;
}

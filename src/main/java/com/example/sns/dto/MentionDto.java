package com.example.sns.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 본문에 적힌 @아이디와 그 사람의 id.
 *
 * 아이디만으로는 프로필 링크를 만들 수 없어서 서버가 id를 같이 내려준다.
 * 프로필 주소는 예전에 아이디 기반에서 id 기반으로 옮겼다. 아이디를 바꾸면
 * 기존 주소가 404가 되던 문제 때문이었고, 그 방식으로 되돌아갈 수는 없다.
 */
@Getter
@AllArgsConstructor
public class MentionDto {
    private String username;
    private Long userId;
}

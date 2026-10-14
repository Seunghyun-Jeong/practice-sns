package com.example.sns.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 팔로워 / 팔로잉 목록에 표시할 유저 정보.
 * followedAt, followId는 화면이 다음 묶음을 달라고 할 때 기준으로 돌려보내는 값이다.
 * 쿼리가 이 생성자로 바로 만들므로 필드 순서를 바꾸면 FollowRepository의 쿼리도 같이 바꿔야 한다.
 */
@Getter
@AllArgsConstructor
public class FollowUserDto {
    private Long id;
    private String username;
    private String profileImageUrl;
    private LocalDateTime followedAt;
    private Long followId;
}

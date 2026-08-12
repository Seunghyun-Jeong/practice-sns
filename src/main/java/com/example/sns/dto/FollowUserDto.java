package com.example.sns.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 팔로워 / 팔로잉 목록에 표시할 유저 정보 */
@Getter
@AllArgsConstructor
public class FollowUserDto {
    private Long id;
    private String username;
    private String profileImageUrl;
}

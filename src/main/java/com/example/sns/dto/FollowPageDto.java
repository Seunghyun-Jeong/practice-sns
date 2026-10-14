package com.example.sns.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 팔로워 / 팔로잉 목록 한 묶음 */
@Getter
@AllArgsConstructor
public class FollowPageDto {
    private List<FollowUserDto> users;
    private boolean hasNext;
}

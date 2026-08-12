package com.example.sns.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 검색 결과 (유저 + 해시태그) */
@Getter
@AllArgsConstructor
public class SearchResultDto {

    @Getter
    @AllArgsConstructor
    public static class UserResult {
        private Long id;
        private String username;
        private String profileImageUrl;
    }

    @Getter
    @AllArgsConstructor
    public static class HashtagResult {
        private String name;
        private long postCount;
    }

    private List<UserResult> users;
    private List<HashtagResult> hashtags;
}

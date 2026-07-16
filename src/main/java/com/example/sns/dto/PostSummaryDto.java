package com.example.sns.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PostSummaryDto {
    private Long id;
    private String author;
    private String createdAt;
    private String updatedAt;
    private String content;
    private String imageUrl;
    private long likeCount;
    private int commentCount;
    private boolean likedByCurrentUser;
    private boolean suspended;
}

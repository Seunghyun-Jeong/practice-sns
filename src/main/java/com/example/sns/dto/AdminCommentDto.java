package com.example.sns.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AdminCommentDto {
    private Long id;
    private String content;
    private String createdAt;
    private Long postId;
    private String postContent;
    private String postAuthor;
}

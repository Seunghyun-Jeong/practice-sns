package com.example.sns.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class CommentDto {
    private Long id;
    private String author;
    private Long authorId;
    private String content;
    private String createdAt;
    private long likeCount;
    private boolean likedByCurrentUser;
}

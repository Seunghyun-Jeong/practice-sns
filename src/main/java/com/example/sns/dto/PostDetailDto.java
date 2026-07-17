package com.example.sns.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PostDetailDto {
    private Long id;
    private String content;
    private String imageUrl;
    private String author;
    private Long authorId;
    private String authorProfileImageUrl;
    private String createdAt;
    private String updatedAt;
    private long likeCount;
    private int commentCount;
    private List<CommentDto> comments;
    private boolean likedByCurrentUser;
    private boolean suspended;
}

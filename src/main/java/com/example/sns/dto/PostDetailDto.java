package com.example.sns.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PostDetailDto {
    private Long id;
    private String title;
    private String content;
    private String author;
    private String createdAt;
    private int likeCount;
    private int commentCount;
    private List<CommentDto> comments;
}

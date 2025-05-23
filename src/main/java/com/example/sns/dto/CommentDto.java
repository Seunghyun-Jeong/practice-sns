package com.example.sns.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CommentDto {
    private Long id;
    private String author;
    private String content;
    private String createdAt;
}

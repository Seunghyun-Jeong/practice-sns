package com.example.sns.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PostResponse {
    private Long id;
    private String content;
    private String imageUrl;
    private String author;
    private LocalDateTime createdAt;
}

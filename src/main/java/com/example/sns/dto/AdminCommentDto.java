package com.example.sns.dto;

import java.time.LocalDateTime;
import lombok.Getter;

/** 관리자 화면(사용자별 작성 댓글)의 댓글 한 줄. 조회에서 바로 만든다(CommentRepository.findAdminCommentsByAuthor) */
@Getter
public class AdminCommentDto {
    private final Long id;
    private final String content;
    private final String createdAt;
    private final Long postId;
    private final String postContent;
    private final String postAuthor;

    public AdminCommentDto(Long id, String content, LocalDateTime createdAt,
                           Long postId, String postContent, String postAuthor) {
        this.id = id;
        this.content = content;
        this.createdAt = createdAt.toString();
        this.postId = postId;
        this.postContent = postContent;
        this.postAuthor = postAuthor;
    }
}

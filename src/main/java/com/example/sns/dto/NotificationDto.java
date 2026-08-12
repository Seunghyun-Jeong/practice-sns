package com.example.sns.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 화면에 표시할 알림 한 건 */
@Getter
@AllArgsConstructor
public class NotificationDto {
    private Long id;
    private String type;
    private String actorUsername;
    private Long actorId;
    private String actorProfileImageUrl;
    /** 좋아요·댓글 알림이 가리키는 게시글 (팔로우 알림은 null) */
    private Long postId;
    private String postImageUrl;
    /** 댓글 알림일 때 보여줄 댓글 내용 (그 외에는 null) */
    private String commentContent;
    private String message;
    private String createdAt;
    private boolean read;
}

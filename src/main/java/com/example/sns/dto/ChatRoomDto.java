package com.example.sns.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 채팅 목록에 표시할 방 한 개 */
@Getter
@AllArgsConstructor
public class ChatRoomDto {
    private Long roomId;
    private Long partnerId;
    private String partnerUsername;
    private String partnerProfileImageUrl;
    /** 목록 미리보기용 마지막 메시지 (아직 대화가 없으면 null) */
    private String lastMessage;
    private String lastMessageAt;
    private long unreadCount;
}

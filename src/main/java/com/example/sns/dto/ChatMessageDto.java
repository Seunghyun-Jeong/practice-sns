package com.example.sns.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 채팅 메시지 한 건 */
@Getter
@AllArgsConstructor
public class ChatMessageDto {
    private Long id;
    private Long roomId;
    private Long senderId;
    private String senderUsername;
    private String content;
    private String createdAt;
    /** 상대가 읽었는지 (내가 보낸 메시지의 읽음 표시에 사용) */
    private boolean read;
    /** 수정된 메시지인지 ("수정됨" 표시용) */
    private boolean edited;
    /** 삭제된 메시지인지 (이 경우 content 는 비어 있다) */
    private boolean deleted;
}

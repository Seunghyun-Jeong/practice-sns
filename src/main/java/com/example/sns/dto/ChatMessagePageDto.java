package com.example.sns.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 채팅 메시지 한 묶음 (최신순, "이전 메시지 보기"로 그 앞 묶음을 불러온다) */
@Getter
@AllArgsConstructor
public class ChatMessagePageDto {
    private List<ChatMessageDto> messages;
    private boolean hasNext;
}

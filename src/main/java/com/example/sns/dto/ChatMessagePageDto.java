package com.example.sns.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 채팅 메시지 한 페이지 (최신순, 위로 스크롤하며 이전 페이지를 불러온다) */
@Getter
@AllArgsConstructor
public class ChatMessagePageDto {
    private List<ChatMessageDto> messages;
    private int page;
    private boolean hasNext;
}

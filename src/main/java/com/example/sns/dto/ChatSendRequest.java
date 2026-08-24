package com.example.sns.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 메시지 전송 요청 */
@Getter
@Setter
@NoArgsConstructor
public class ChatSendRequest {
    private String content;
}

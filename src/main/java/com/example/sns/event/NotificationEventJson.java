package com.example.sns.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * NotificationEvent를 글자로 적고 다시 읽는 곳. 쓰는 쪽(Outbox 저장)과 읽는 쪽(Kafka 소비자)이
 * 같은 모양을 써야 하므로 한 곳에 둔다. 릴레이는 Outbox의 JSON을 그대로 Kafka 메시지 값으로 보낸다.
 *
 * Spring Boot가 만든 ObjectMapper를 쓴다. LocalDateTime을 ISO 문자열로 적는 설정이 이미 들어 있다.
 */
@Component
@RequiredArgsConstructor
public class NotificationEventJson {
    private final ObjectMapper objectMapper;

    public String write(NotificationEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("알림 이벤트를 JSON으로 바꾸지 못했다: " + event.eventId(), e);
        }
    }

    public NotificationEvent read(String json) {
        try {
            return objectMapper.readValue(json, NotificationEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("알림 이벤트 JSON을 읽지 못했다", e);
        }
    }
}

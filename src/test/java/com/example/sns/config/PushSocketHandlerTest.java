package com.example.sns.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * 실시간 푸시는 보낼 내용을 받을 사람이 접속해 있을 때만 만든다.
 * 배지 숫자처럼 만드는 데 쿼리가 드는 내용이 아무도 받지 않는데 계산되면 안 된다.
 */
class PushSocketHandlerTest {

    private final PushSocketHandler handler = new PushSocketHandler(new ObjectMapper());
    private final AtomicInteger made = new AtomicInteger();

    @Test
    @DisplayName("받을 사람이 접속해 있지 않으면 보낼 내용을 만들지도 않는다")
    void 접속하지_않았으면_만들지_않는다() {
        handler.pushToUser(1L, this::badge);

        assertThat(made.get()).isZero();
    }

    @Test
    @DisplayName("받을 사람이 접속해 있으면 내용을 만들어 보낸다")
    void 접속해_있으면_만들어_보낸다() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userId", 1L);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(session);

        handler.pushToUser(1L, this::badge);

        assertThat(made.get()).isEqualTo(1);
        verify(session).sendMessage(any(TextMessage.class));
    }

    private Map<String, Object> badge() {
        made.incrementAndGet();
        return Map.of("type", "noti-badge", "count", 3);
    }
}

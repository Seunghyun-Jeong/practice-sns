package com.example.sns.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 서버 푸시 전용 WebSocket 핸들러.
 * 클라이언트가 서버로 보내는 건 없고(쓰기는 REST 그대로),
 * 새 채팅 메시지나 알림처럼 "생겼다"는 사실을 접속 중인 유저에게 밀어주는 용도로만 쓴다.
 *
 * 같은 유저가 탭을 여러 개 열 수 있으므로 유저당 세션을 Set으로 들고 있는다.
 */
@Component
@RequiredArgsConstructor
public class PushSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;

    /** 접속 중인 유저 id → 그 유저의 소켓 세션들 */
    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) {
            return;
        }
        sessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) {
            return;
        }
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions != null) {
            userSessions.remove(session);
            if (userSessions.isEmpty()) {
                sessions.remove(userId);
            }
        }
    }

    /** 접속 중이면 밀어주고, 아니면 조용히 넘어간다 (데이터는 DB에 있으니 다음 접속 때 보인다) */
    public void pushToUser(Long userId, Object payload) {
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null || userSessions.isEmpty()) {
            return;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (IOException e) {
            return;
        }

        for (WebSocketSession session : userSessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            } catch (IOException e) {
                // 이 세션만 실패한 것이므로 나머지 세션 전송은 계속한다
            }
        }
    }
}

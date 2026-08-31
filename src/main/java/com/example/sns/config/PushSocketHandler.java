package com.example.sns.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 서버 푸시 WebSocket 핸들러.
 * 새 채팅 메시지나 알림처럼 "생겼다"는 사실을 접속 중인 유저에게 밀어준다.
 * 클라이언트가 서버로 보내는 것은 하트비트 응답(pong)뿐이고, 쓰기는 REST 그대로다.
 *
 * 같은 유저가 탭을 여러 개 열 수 있으므로 유저당 세션을 Set으로 들고 있는다.
 */
@Component
@RequiredArgsConstructor
public class PushSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;

    /** 하트비트를 보내는 주기 */
    private static final long PING_INTERVAL_MS = 30_000L;

    /**
     * 이 시간 동안 응답이 없으면 죽은 연결로 본다.
     * 주기의 3배로 잡아 한두 번의 일시적 지연으로는 끊기지 않게 한다.
     */
    private static final long DEAD_AFTER_MS = PING_INTERVAL_MS * 3;

    /** 접속 중인 유저 id → 그 유저의 소켓 세션들 */
    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    /** 세션별 마지막 응답 시각. 값이 오래되면 연결이 죽은 것으로 판단한다 */
    private final Map<WebSocketSession, Long> lastSeen = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) {
            return;
        }
        sessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
        lastSeen.put(session, System.currentTimeMillis());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        lastSeen.remove(session);

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

    /** 클라이언트가 보내는 것은 하트비트 응답뿐이다 */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        if (message.getPayload().contains("\"pong\"")) {
            lastSeen.put(session, System.currentTimeMillis());
        }
    }

    /**
     * 주기적으로 하트비트를 보낸다.
     * 이유가 두 가지다.
     * 1. 이 소켓은 푸시 전용이라 대화가 없으면 트래픽이 0인데,
     *    프록시나 로드밸런서는 일정 시간 조용한 연결을 끊는다. 주기적 신호로 그것을 막는다.
     * 2. 응답이 끊긴 세션을 찾아내 정리한다. 연결이 실제로는 죽었는데
     *    양쪽 모두 살아 있다고 믿는 상태가 생길 수 있기 때문이다.
     */
    @Scheduled(fixedRate = PING_INTERVAL_MS)
    public void sendHeartbeat() {
        long now = System.currentTimeMillis();

        for (Set<WebSocketSession> userSessions : sessions.values()) {
            for (WebSocketSession session : userSessions) {
                Long seen = lastSeen.get(session);

                // 오래 응답이 없으면 죽은 연결로 보고 닫는다.
                // 닫아야 클라이언트도 재연결과 폴링을 다시 시작할 수 있다.
                if (seen != null && now - seen > DEAD_AFTER_MS) {
                    closeQuietly(session);
                    continue;
                }

                try {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage("{\"type\":\"ping\"}"));
                    }
                } catch (IOException e) {
                    closeQuietly(session);
                }
            }
        }
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.SESSION_NOT_RELIABLE);
        } catch (IOException ignored) {
            // 이미 끊긴 세션이면 닫기도 실패할 수 있다. afterConnectionClosed 에서 정리된다.
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

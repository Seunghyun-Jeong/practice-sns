package com.example.sns.config;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * 채팅 실시간 푸시용 WebSocket 설정.
 * 핸드셰이크는 일반 HTTP 요청이라 JwtAuthFilter를 그대로 지나오므로,
 * 거기서 인증된 사용자 id를 소켓 세션에 옮겨 담기만 한다.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {
    private final PushSocketHandler pushSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(pushSocketHandler, "/ws")
                .addInterceptors(new AuthHandshakeInterceptor());
    }

    /** 핸드셰이크 시점의 인증 정보에서 userId를 꺼내 세션 속성에 넣는다 */
    static class AuthHandshakeInterceptor implements HandshakeInterceptor {
        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof MyUserDetails userDetails) {
                attributes.put("userId", userDetails.getUserId());
                return true;
            }
            return false;   // 비로그인 연결은 거부
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
        }
    }
}

package com.example.sns.config;

import com.example.sns.entity.User;
import com.example.sns.repository.UserRepository;
import com.example.sns.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 인증이 필요한 요청이 차단될 때, 사유에 맞는 일관된 JSON 메시지를 반환한다.
 * - 정지된 계정: 403 "현재 이용이 정지된 계정입니다."
 * - 그 외(비로그인 등): 401 "로그인이 필요합니다."
 */
@Component
@RequiredArgsConstructor
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String message = "로그인이 필요합니다.";
        int status = HttpServletResponse.SC_UNAUTHORIZED;

        String token = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("JWT_TOKEN".equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }

        if (token != null && jwtUtil.validateToken(token)) {
            String username = jwtUtil.getUsernameFromToken(token);
            boolean suspended = userRepository.findByUsername(username)
                    .map(User::isSuspended)
                    .orElse(false);
            if (suspended) {
                message = "현재 이용이 정지된 계정입니다.";
                status = HttpServletResponse.SC_FORBIDDEN;
            }
        }

        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}

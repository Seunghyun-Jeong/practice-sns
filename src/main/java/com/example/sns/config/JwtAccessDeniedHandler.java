package com.example.sns.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * 로그인은 했지만 권한이 부족할 때(403)의 응답.
 *
 * API는 JSON 메시지를 받고(예: 일반 사용자가 관리자용 정지 API를 호출),
 * 관리자 페이지 요청은 홈으로 돌려보낸다.
 */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        if (AuthFailureResponder.isApiRequest(request)) {
            AuthFailureResponder.sendJson(response, HttpServletResponse.SC_FORBIDDEN, "권한이 없습니다.");
            return;
        }
        AuthFailureResponder.redirectHome(response);
    }
}

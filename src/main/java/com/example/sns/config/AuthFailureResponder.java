package com.example.sns.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 인증과 인가가 실패했을 때의 응답 형태를 한 곳에서 정한다.
 *
 * API는 JSON을 기대하는 호출자(fetch)가 받고, SSR 페이지는 사람이 주소창으로 연다.
 * 페이지 요청에 JSON을 내려주면 화면에 {"message":"..."}가 그대로 찍힌다.
 *
 * 판단 기준을 Accept 헤더가 아니라 경로로 둔 이유는, 이 앱이 이미 /api/와 SSR 경로로
 * 나뉘어 있고 헤더는 호출자가 어떻게 보내느냐에 따라 달라지기 때문이다.
 */
final class AuthFailureResponder {

    private AuthFailureResponder() {
    }

    static boolean isApiRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/api/") || uri.startsWith("/ws");
    }

    static void sendJson(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }

    /**
     * 페이지 요청은 조용히 홈으로 보낸다.
     * 로그인 화면으로 보내면 "로그인하면 볼 수 있는 페이지"라는 것을 알려주는 셈이 된다.
     */
    static void redirectHome(HttpServletResponse response) throws IOException {
        response.sendRedirect("/");
    }
}

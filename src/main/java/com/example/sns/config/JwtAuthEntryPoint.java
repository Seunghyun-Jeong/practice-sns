package com.example.sns.config;

import com.example.sns.entity.User;
import com.example.sns.repository.UserRepository;
import com.example.sns.service.RefreshTokenService;
import com.example.sns.util.JwtUtil;
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
    private final RefreshTokenService refreshTokenService;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String message = "로그인이 필요합니다.";
        int status = HttpServletResponse.SC_UNAUTHORIZED;

        if (isSuspended(resolveUsername(request))) {
            message = "현재 이용이 정지된 계정입니다.";
            status = HttpServletResponse.SC_FORBIDDEN;
        }

        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }

    /**
     * 요청이 누구의 것인지 알아낸다.
     *
     * 액세스 토큰만 보면 안 된다. 액세스 토큰이 만료된 채 리프레시 토큰으로 들어오는 요청이
     * 정상 경로이기 때문이다. 그 경우까지 보지 않으면 정지된 계정이 정지 안내 대신
     * "로그인이 필요합니다"를 받게 된다.
     */
    private String resolveUsername(HttpServletRequest request) {
        String accessToken = AuthTokenIssuer.readCookie(request, AuthTokenIssuer.ACCESS_COOKIE);
        if (accessToken != null && jwtUtil.validateToken(accessToken)) {
            return jwtUtil.getUsernameFromToken(accessToken);
        }

        // 여기서는 회전시키지 않는 조회만 쓴다. 에러 응답을 만들면서 토큰을 바꿔서는 안 된다.
        return refreshTokenService.findOwner(AuthTokenIssuer.readCookie(request, AuthTokenIssuer.REFRESH_COOKIE))
                .map(User::getUsername)
                .orElse(null);
    }

    private boolean isSuspended(String username) {
        if (username == null) {
            return false;
        }
        return userRepository.findByUsername(username)
                .map(User::isSuspended)
                .orElse(false);
    }
}

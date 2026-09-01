package com.example.sns.config;

import com.example.sns.entity.User;
import com.example.sns.service.RefreshTokenService;
import com.example.sns.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 로그인 상태를 나타내는 쿠키를 한 곳에서 만든다.
 *
 * 예전에 토큰 검증이 컨트롤러 28곳에 복사되어 있다가, 새로 만든 컨트롤러에서 빠지면서
 * 인가 구멍이 생긴 적이 있다. 쿠키를 굽는 코드도 같은 성질이라 여기로 모았다.
 * 쿠키 이름이나 수명을 바꿀 일이 생겨도 고칠 곳이 하나다.
 */
@Component
@RequiredArgsConstructor
public class AuthTokenIssuer {
    public static final String ACCESS_COOKIE = "JWT_TOKEN";
    public static final String REFRESH_COOKIE = "REFRESH_TOKEN";

    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;

    /** 로그인 성공. 액세스와 리프레시를 함께 발급한다 */
    public void issueLogin(User user, HttpServletResponse response) {
        writeAccessToken(user, response);
        writeRefreshCookie(refreshTokenService.issue(user), response);
    }

    /**
     * 액세스 토큰만 새로 굽는다.
     * 자동 갱신과 닉네임 변경(토큰에 든 닉네임이 옛것이 되므로)에서 쓴다.
     */
    public void writeAccessToken(User user, HttpServletResponse response) {
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole().name());
        write(response, ACCESS_COOKIE, token, JwtUtil.VALIDITY);
    }

    /** 회전으로 새 리프레시 토큰이 나왔을 때 쓴다 */
    public void writeRefreshCookie(String rawToken, HttpServletResponse response) {
        write(response, REFRESH_COOKIE, rawToken, RefreshTokenService.VALIDITY);
    }

    /** 로그아웃과 탈퇴. 브라우저에서 두 쿠키를 모두 지운다 */
    public void clear(HttpServletResponse response) {
        write(response, ACCESS_COOKIE, "", Duration.ZERO);
        write(response, REFRESH_COOKIE, "", Duration.ZERO);
    }

    public static String readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * 쿠키 수명을 토큰 수명과 똑같이 맞춘다.
     * 예전에는 토큰이 1시간인데 쿠키가 24시간이라, 이미 죽은 토큰을 브라우저가 23시간 더 보내고 있었다.
     */
    private void write(HttpServletResponse response, String name, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(false)      // 로컬은 http라 false. 배포하면 true로 올려야 한다
                .path("/")
                .sameSite("Lax")
                .maxAge(maxAge)
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }
}

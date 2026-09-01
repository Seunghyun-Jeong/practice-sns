package com.example.sns.config;

import com.example.sns.entity.User;
import com.example.sns.service.MyUserDetailService;
import com.example.sns.service.RefreshTokenService;
import com.example.sns.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final MyUserDetailService myUserDetailService;
    private final RefreshTokenService refreshTokenService;
    private final AuthTokenIssuer authTokenIssuer;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String accessToken = AuthTokenIssuer.readCookie(request, AuthTokenIssuer.ACCESS_COOKIE);

        if (accessToken != null && jwtUtil.validateToken(accessToken)) {
            authenticate(jwtUtil.getUsernameFromToken(accessToken), request);
        } else {
            // 액세스 토큰이 없거나 만료됐다. 리프레시 토큰이 살아 있으면 조용히 다시 발급한다.
            // 여기서 처리하는 이유는 이 앱이 SSR이기 때문이다. 페이지를 여는 순간 이미 로그인
            // 여부가 정해지므로, 브라우저에서 401을 받고 재시도하는 방식으로는
            // "오랜만에 다시 들어왔을 때 로그인이 유지된다"를 만들 수 없다.
            refreshAccessToken(request, response);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 정적 리소스는 인증 정보를 쓰지 않는다.
     * 액세스 토큰이 만료된 직후의 페이지 로드에서 이것들까지 갱신을 시도하면 DB 조회만 늘어난다.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/images/")
                || path.startsWith("/uploads/")
                || path.equals("/favicon.ico");
    }

    private void refreshAccessToken(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = AuthTokenIssuer.readCookie(request, AuthTokenIssuer.REFRESH_COOKIE);

        Optional<User> owner = refreshTokenService.findOwner(refreshToken);
        if (owner.isEmpty()) {
            return;
        }

        // 회전보다 인증이 먼저다. 정지된 계정처럼 인증에 실패하는 경우에 토큰을 태워버리면,
        // 정지가 풀렸을 때 그 사람은 이유 없이 로그아웃되어 있다.
        if (!authenticate(owner.get().getUsername(), request)) {
            return;
        }

        authTokenIssuer.writeAccessToken(owner.get(), response);

        // 회전된 경우에만 리프레시 쿠키도 새로 내린다.
        // 유예 시간에 걸려 통과한 요청이면 클라이언트가 이미 새 토큰을 받아갔다.
        refreshTokenService.rotate(refreshToken)
                .ifPresent(rotated -> authTokenIssuer.writeRefreshCookie(rotated, response));
    }

    /**
     * 액세스 토큰으로 들어오든 리프레시 토큰으로 들어오든 인증은 여기 하나를 지난다.
     * 예전에 인증 경로가 두 갈래여서, 필터에 넣은 정지 계정 검사가 다른 갈래에는
     * 적용되지 않았던 적이 있다. 그 구조를 다시 만들지 않으려고 하나로 모았다.
     */
    private boolean authenticate(String username, HttpServletRequest request) {
        try {
            UserDetails userDetails = myUserDetailService.loadUserByUsername(username);

            // 정지된(비활성) 계정은 인증하지 않아 쓰기 동작을 차단한다.
            if (!userDetails.isEnabled()) {
                return false;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            return true;
        } catch (UsernameNotFoundException e) {
            // 탈퇴 등으로 사라진 사용자의 토큰 — 인증 없이 통과시킨다.
            return false;
        }
    }
}

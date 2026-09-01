package com.example.sns.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sns.config.AuthTokenIssuer;
import com.example.sns.entity.User;
import com.example.sns.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 액세스 토큰이 만료된 뒤에도 로그인이 유지되는지 확인한다.
 *
 * 이 앱은 SSR이라 갱신을 필터에서 한다. 그래서 "브라우저가 401을 받고 재시도한다"가 아니라
 * "만료된 액세스 토큰으로 그냥 요청해도 통과하고, 응답에 새 쿠키가 딸려 온다"가 기대 동작이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RefreshTokenApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Value("${jwt.secret}")
    private String secret;

    /** 로그인까지 마치고 받은 쿠키들을 돌려준다 */
    private Cookie[] signUpAndLogin(String username) throws Exception {
        mockMvc.perform(post("/api/users/signup")
                .param("username", username)
                .param("password", "Test1234!"));

        MvcResult result = mockMvc.perform(post("/api/users/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"Test1234!\"}"))
                .andExpect(status().isOk())
                .andReturn();

        return result.getResponse().getCookies();
    }

    private Cookie pick(Cookie[] cookies, String name) {
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie;
            }
        }
        return null;
    }

    /**
     * 이미 만료된 액세스 토큰을 직접 만든다.
     * 30분을 기다릴 수는 없으므로 서명 키만 같게 해서 과거 시각으로 굽는다.
     */
    private Cookie expiredAccessToken(String username) {
        long now = System.currentTimeMillis();
        String token = Jwts.builder()
                .setSubject(username)
                .claim("userId", 1L)
                .claim("role", "USER")
                .setIssuedAt(new Date(now - 7_200_000))
                .setExpiration(new Date(now - 3_600_000))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();

        return new Cookie(AuthTokenIssuer.ACCESS_COOKIE, token);
    }

    @Test
    @DisplayName("로그인하면 액세스 쿠키와 리프레시 쿠키를 함께 받는다")
    void 로그인하면_쿠키_두_개를_받는다() throws Exception {
        Cookie[] cookies = signUpAndLogin("reftest1");

        assertThat(pick(cookies, AuthTokenIssuer.ACCESS_COOKIE)).isNotNull();
        assertThat(pick(cookies, AuthTokenIssuer.REFRESH_COOKIE)).isNotNull();
    }

    @Test
    @DisplayName("쿠키 수명이 토큰 수명과 어긋나지 않는다")
    void 쿠키_수명이_토큰_수명과_같다() throws Exception {
        Cookie[] cookies = signUpAndLogin("reftest2");

        // 예전에는 토큰이 1시간인데 쿠키가 24시간이라 죽은 토큰을 계속 보내고 있었다
        assertThat(pick(cookies, AuthTokenIssuer.ACCESS_COOKIE).getMaxAge()).isEqualTo(30 * 60);
        assertThat(pick(cookies, AuthTokenIssuer.REFRESH_COOKIE).getMaxAge()).isEqualTo(14 * 24 * 60 * 60);
    }

    @Test
    @DisplayName("액세스 토큰이 만료돼도 리프레시 토큰이 있으면 요청이 통과한다")
    void 만료된_액세스는_자동으로_갱신된다() throws Exception {
        Cookie[] cookies = signUpAndLogin("reftest3");
        Cookie refresh = pick(cookies, AuthTokenIssuer.REFRESH_COOKIE);

        mockMvc.perform(get("/api/notifications")
                        .cookie(expiredAccessToken("reftest3"), refresh))
                .andExpect(status().isOk())
                // 갱신됐다는 증거로 새 액세스 쿠키가 응답에 실려 온다
                .andExpect(cookie().exists(AuthTokenIssuer.ACCESS_COOKIE));
    }

    @Test
    @DisplayName("갱신되면 리프레시 토큰도 새 값으로 회전된다")
    void 갱신하면_리프레시도_회전된다() throws Exception {
        Cookie[] cookies = signUpAndLogin("reftest4");
        Cookie refresh = pick(cookies, AuthTokenIssuer.REFRESH_COOKIE);

        MvcResult result = mockMvc.perform(get("/api/notifications")
                        .cookie(expiredAccessToken("reftest4"), refresh))
                .andExpect(status().isOk())
                .andReturn();

        Cookie rotated = pick(result.getResponse().getCookies(), AuthTokenIssuer.REFRESH_COOKIE);
        assertThat(rotated).isNotNull();
        assertThat(rotated.getValue()).isNotEqualTo(refresh.getValue());
    }

    @Test
    @DisplayName("리프레시 토큰이 없으면 만료된 액세스로는 통과하지 못한다")
    void 리프레시가_없으면_401() throws Exception {
        mockMvc.perform(post("/api/users/signup")
                .param("username", "reftest5")
                .param("password", "Test1234!"));

        mockMvc.perform(get("/api/notifications")
                        .cookie(expiredAccessToken("reftest5")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그아웃하면 리프레시 토큰이 무효가 되어 더는 갱신되지 않는다")
    void 로그아웃하면_갱신도_막힌다() throws Exception {
        Cookie[] cookies = signUpAndLogin("reftest6");
        Cookie refresh = pick(cookies, AuthTokenIssuer.REFRESH_COOKIE);

        mockMvc.perform(post("/api/users/logout").cookie(refresh))
                .andExpect(status().isOk());

        // 브라우저에서 쿠키를 지우는 것과 별개로, 그 값 자체가 이제 서버에서 통하지 않아야 한다
        mockMvc.perform(get("/api/notifications")
                        .cookie(expiredAccessToken("reftest6"), refresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("회전된 뒤에 로그아웃해도 새 토큰까지 함께 무효가 된다")
    void 로그아웃은_회전된_토큰까지_끊는다() throws Exception {
        Cookie[] cookies = signUpAndLogin("reftest8");
        Cookie first = pick(cookies, AuthTokenIssuer.REFRESH_COOKIE);

        // 한 번 갱신해서 토큰을 회전시킨다
        MvcResult refreshed = mockMvc.perform(get("/api/notifications")
                        .cookie(expiredAccessToken("reftest8"), first))
                .andExpect(status().isOk())
                .andReturn();
        Cookie rotated = pick(refreshed.getResponse().getCookies(), AuthTokenIssuer.REFRESH_COOKIE);

        // 옛 토큰을 들고 로그아웃한다 (유예 시간 안이라 서버는 받아준다)
        mockMvc.perform(post("/api/users/logout").cookie(first))
                .andExpect(status().isOk());

        // 제시된 값만 지우면 회전으로 생긴 이 토큰이 살아남아 계속 재발급을 받게 된다
        mockMvc.perform(get("/api/notifications")
                        .cookie(expiredAccessToken("reftest8"), rotated))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("정지된 계정은 리프레시 토큰으로도 인증되지 않는다")
    void 정지된_계정은_갱신되지_않는다() throws Exception {
        Cookie[] cookies = signUpAndLogin("reftest7");
        Cookie refresh = pick(cookies, AuthTokenIssuer.REFRESH_COOKIE);

        // 정지 검사가 액세스 토큰 경로에만 걸려 있으면 여기로 빠져나갈 수 있다.
        // 예전에 인증 경로가 두 갈래여서 같은 종류의 구멍이 났던 적이 있다.
        User user = userRepository.findByUsername("reftest7").orElseThrow();
        user.setSuspendedUntil(LocalDateTime.now().plusDays(1));
        userRepository.save(user);

        mockMvc.perform(get("/api/notifications")
                        .cookie(expiredAccessToken("reftest7"), refresh))
                .andExpect(status().isForbidden());
    }
}

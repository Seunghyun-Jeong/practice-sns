package com.example.sns.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.sns.entity.User;
import com.example.sns.repository.UserRepository;
import com.example.sns.service.RefreshTokenService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 액세스 토큰이 만료된 뒤 리프레시 토큰으로 다시 들어올 때 나가는 쿼리 수.
 *
 * 토큰과 주인을 한 번에 읽고, 교체 표시를 찍고, 새 토큰을 저장하는 세 번이면 된다.
 * 주인을 아이디로 다시 찾거나 회전할 때 토큰을 다시 읽으면 같은 행을 또 읽는 것이다.
 * 페이지 요청만 보도록 필터 하나만 돌린다.
 */
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN"
})
@Transactional
class RefreshQueryCountTest {

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("리프레시 토큰으로 다시 들어오면 조회, 교체 표시, 새 토큰 저장 세 번으로 끝난다")
    void 리프레시로_갱신하면_쿼리_세_번() throws Exception {
        User user = new User();
        user.setUsername("tester");
        user.setPassword("password");
        user.setRole(User.Role.USER);
        userRepository.save(user);
        String raw = refreshTokenService.issue(user);
        entityManager.flush();
        entityManager.clear();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setCookies(new Cookie(AuthTokenIssuer.REFRESH_COOKIE, raw));
        MockHttpServletResponse response = new MockHttpServletResponse();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        jwtAuthFilter.doFilter(request, response, new MockFilterChain());
        long queries = statistics.getPrepareStatementCount();

        // 인증되고, 액세스 쿠키와 회전된 리프레시 쿠키를 모두 받는다
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("tester");
        List<String> cookies = response.getHeaders("Set-Cookie");
        assertThat(cookies).anyMatch(c -> c.startsWith(AuthTokenIssuer.ACCESS_COOKIE + "="));
        assertThat(cookies).anyMatch(c -> c.startsWith(AuthTokenIssuer.REFRESH_COOKIE + "=")
                && !c.startsWith(AuthTokenIssuer.REFRESH_COOKIE + "=" + raw));
        assertThat(queries).as("리프레시 갱신 쿼리 %d회", queries).isEqualTo(3);
    }
}

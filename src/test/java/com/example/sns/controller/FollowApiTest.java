package com.example.sns.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sns.entity.User;
import com.example.sns.repository.UserRepository;
import com.example.sns.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팔로우 / 알림 API 테스트.
 * 로그인 상태는 실제 로그인처럼 JWT 쿠키를 실어서 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FollowApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    private User me;
    private User other;
    private Cookie myCookie;

    @BeforeEach
    void setUp() {
        me = createUser("tester1");
        other = createUser("tester2");
        myCookie = createCookie(me);
    }

    private User createUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("Test1234!"));
        user.setRole(User.Role.USER);
        return userRepository.save(user);
    }

    private Cookie createCookie(User user) {
        return new Cookie("JWT_TOKEN",
                jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole().name()));
    }

    @Test
    @DisplayName("로그인하지 않으면 팔로우할 수 없다")
    void 비로그인_팔로우는_401() throws Exception {
        mockMvc.perform(post("/api/users/{id}/follow", other.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("팔로우를 누르면 팔로우되고 다시 누르면 취소된다")
    void 팔로우_토글() throws Exception {
        mockMvc.perform(post("/api/users/{id}/follow", other.getId()).cookie(myCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.following").value(true))
                .andExpect(jsonPath("$.followerCount").value(1));

        mockMvc.perform(post("/api/users/{id}/follow", other.getId()).cookie(myCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.following").value(false))
                .andExpect(jsonPath("$.followerCount").value(0));
    }

    @Test
    @DisplayName("자기 자신을 팔로우하면 400을 준다")
    void 자기자신_팔로우는_400() throws Exception {
        mockMvc.perform(post("/api/users/{id}/follow", me.getId()).cookie(myCookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("자기 자신은 팔로우할 수 없습니다."));
    }

    @Test
    @DisplayName("팔로우하면 상대의 팔로워 목록에 내가 보인다")
    void 팔로워_목록에_보인다() throws Exception {
        mockMvc.perform(post("/api/users/{id}/follow", other.getId()).cookie(myCookie));

        mockMvc.perform(get("/api/users/{id}/followers", other.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[0].username").value("tester1"));
    }

    @Test
    @DisplayName("팔로우하면 상대에게 알림이 생기고, 취소하면 사라진다")
    void 팔로우_알림() throws Exception {
        Cookie otherCookie = createCookie(other);

        mockMvc.perform(post("/api/users/{id}/follow", other.getId()).cookie(myCookie));
        mockMvc.perform(get("/api/notifications/unread-count").cookie(otherCookie))
                .andExpect(jsonPath("$.count").value(1));

        mockMvc.perform(post("/api/users/{id}/follow", other.getId()).cookie(myCookie));
        mockMvc.perform(get("/api/notifications/unread-count").cookie(otherCookie))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    @DisplayName("로그인하지 않으면 알림 목록을 볼 수 없다")
    void 비로그인_알림조회는_401() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());
    }

    // 탭 전환은 페이지를 다시 불러오지 않고 /feed 조각의 첫 페이지로 컨테이너를 갈아 끼운다.
    // 그래서 팔로잉 빈 안내문이 조각의 첫 페이지에 실려 와야 한다.

    @Test
    @DisplayName("팔로우가 없으면 팔로잉 피드 조각의 첫 페이지에 안내문이 실린다")
    void 팔로잉_조각_첫페이지_빈안내문() throws Exception {
        mockMvc.perform(get("/feed").param("tab", "following").param("page", "0").cookie(myCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("아직 팔로우한 사람이 없습니다")));
    }

    @Test
    @DisplayName("다음 페이지가 비어 있는 것은 끝에 닿은 것이라 안내문을 싣지 않는다")
    void 팔로잉_조각_다음페이지는_안내문없음() throws Exception {
        mockMvc.perform(get("/feed").param("tab", "following").param("page", "1").cookie(myCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("아직 팔로우한 사람이 없습니다"))));
    }

    @Test
    @DisplayName("전체 탭 조각에는 팔로잉 안내문이 실리지 않는다")
    void 전체_조각은_안내문없음() throws Exception {
        mockMvc.perform(get("/feed").param("tab", "all").param("page", "0").cookie(myCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("아직 팔로우한 사람이 없습니다"))));
    }
}

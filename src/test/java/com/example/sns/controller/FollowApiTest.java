package com.example.sns.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}

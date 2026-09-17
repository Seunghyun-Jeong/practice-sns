package com.example.sns.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sns.entity.User;
import com.example.sns.repository.UserRepository;
import com.example.sns.service.ReportService;
import com.example.sns.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 페이지와 관리자 API의 접근 제어.
 *
 * 권한 선언이 SecurityConfig 한 곳으로 모였으므로, 페이지가 늘어나도
 * 이 테스트가 뚫린 경로를 잡아준다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminAccessApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ReportService reportService;

    private User member;
    private User admin;
    private Cookie memberCookie;
    private Cookie adminCookie;

    @BeforeEach
    void setUp() {
        member = createUser("member1", User.Role.USER);
        admin = createUser("admin1", User.Role.ADMIN);
        memberCookie = createCookie(member);
        adminCookie = createCookie(admin);
    }

    private User createUser(String username, User.Role role) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("Test1234!"));
        user.setRole(role);
        return userRepository.save(user);
    }

    private Cookie createCookie(User user) {
        return new Cookie("JWT_TOKEN",
                jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole().name()));
    }

    @Test
    @DisplayName("관리자는 신고 확인 페이지를 볼 수 있다")
    void 관리자는_신고페이지_접근() throws Exception {
        mockMvc.perform(get("/admin/reports").cookie(adminCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/reports").param("tab", "handled").cookie(adminCookie))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("일반 유저가 주소를 직접 입력해도 신고 확인 페이지로 못 들어간다")
    void 일반유저는_신고페이지_차단() throws Exception {
        mockMvc.perform(get("/admin/reports").cookie(memberCookie))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        mockMvc.perform(get("/admin/reports").param("tab", "handled").cookie(memberCookie))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        mockMvc.perform(get("/admin/reports/users/{id}", admin.getId()).cookie(memberCookie))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("비로그인도 신고 확인 페이지로 못 들어간다")
    void 비로그인은_신고페이지_차단() throws Exception {
        mockMvc.perform(get("/admin/reports"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("나머지 관리자 페이지도 일반 유저에게 막혀 있다")
    void 다른_관리자페이지도_차단() throws Exception {
        mockMvc.perform(get("/admin/suspended-users").cookie(memberCookie))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        mockMvc.perform(get("/admin/users/{id}/content", admin.getId()).cookie(memberCookie))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("페이지가 막힐 때는 JSON이 아니라 리다이렉트가 나간다")
    void 페이지_차단은_JSON이_아니다() throws Exception {
        mockMvc.perform(get("/admin/reports").cookie(memberCookie))
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("대기 중인 신고가 없으면 전체 반려 버튼이 비활성화된다")
    void 대기가_없으면_전체반려_비활성화() throws Exception {
        mockMvc.perform(get("/admin/reports/users/{id}", member.getId()).cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(model().attribute("hasPending", false))
                .andExpect(content().string(containsString("id=\"rejectAllBtn\"")))
                .andExpect(content().string(containsString("disabled")));
    }

    @Test
    @DisplayName("대기 중인 신고가 있으면 전체 반려 버튼을 쓸 수 있다")
    void 대기가_있으면_전체반려_활성화() throws Exception {
        reportService.report(admin.getId(), member.getId(), "SPAM", "도배");

        mockMvc.perform(get("/admin/reports/users/{id}", member.getId()).cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(model().attribute("hasPending", true));
    }

    @Test
    @DisplayName("API는 그대로 JSON으로 거절한다")
    void API는_JSON으로_거절() throws Exception {
        mockMvc.perform(patch("/api/reports/users/{id}/reject", admin.getId()).cookie(memberCookie))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("권한이 없습니다."));

        mockMvc.perform(patch("/api/reports/users/{id}/reject", admin.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
    }
}

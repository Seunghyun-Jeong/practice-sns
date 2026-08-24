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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채팅 API 테스트.
 * 로그인 상태는 실제 로그인처럼 JWT 쿠키를 실어서 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChatApiTest {

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
    private Cookie otherCookie;

    @BeforeEach
    void setUp() {
        me = createUser("tester1");
        other = createUser("tester2");
        myCookie = createCookie(me);
        otherCookie = createCookie(other);
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

    private Long openRoom() throws Exception {
        String body = mockMvc.perform(post("/api/chats/with/{id}", other.getId()).cookie(myCookie))
                .andReturn().getResponse().getContentAsString();
        return Long.valueOf(body.replaceAll("\\D", ""));
    }

    private void send(Long roomId, Cookie cookie, String content) throws Exception {
        mockMvc.perform(post("/api/chats/{roomId}/messages", roomId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그인하지 않으면 채팅을 쓸 수 없다")
    void 비로그인은_401() throws Exception {
        mockMvc.perform(get("/api/chats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("같은 상대와 방을 두 번 열어도 방은 하나다")
    void 방은_하나만_생긴다() throws Exception {
        Long first = openRoom();

        // 상대 쪽에서 열어도 같은 방이어야 한다
        mockMvc.perform(post("/api/chats/with/{id}", me.getId()).cookie(otherCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(first));
    }

    @Test
    @DisplayName("자기 자신과는 방을 열 수 없다")
    void 자기자신은_400() throws Exception {
        mockMvc.perform(post("/api/chats/with/{id}", me.getId()).cookie(myCookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("자기 자신과는 대화할 수 없습니다."));
    }

    @Test
    @DisplayName("메시지를 보내면 상대의 메시지 목록에 보인다")
    void 메시지_전송과_조회() throws Exception {
        Long roomId = openRoom();
        send(roomId, myCookie, "안녕하세요");

        mockMvc.perform(get("/api/chats/{roomId}/messages", roomId).cookie(otherCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].content").value("안녕하세요"))
                .andExpect(jsonPath("$.messages[0].senderUsername").value("tester1"));
    }

    @Test
    @DisplayName("참여자가 아닌 사람은 방의 메시지를 볼 수 없다")
    void 남의_방은_400() throws Exception {
        Long roomId = openRoom();
        User stranger = createUser("tester3");

        mockMvc.perform(get("/api/chats/{roomId}/messages", roomId).cookie(createCookie(stranger)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("참여 중인 채팅방이 아닙니다."));
    }

    @Test
    @DisplayName("상대가 보낸 메시지만큼 안읽음 수가 오르고, 읽음 처리하면 0이 된다")
    void 안읽음_수와_읽음_처리() throws Exception {
        Long roomId = openRoom();
        send(roomId, myCookie, "하나");
        send(roomId, myCookie, "둘");

        mockMvc.perform(get("/api/chats/unread-count").cookie(otherCookie))
                .andExpect(jsonPath("$.count").value(2));

        // 내가 보낸 메시지는 나의 안읽음에 포함되지 않는다
        mockMvc.perform(get("/api/chats/unread-count").cookie(myCookie))
                .andExpect(jsonPath("$.count").value(0));

        mockMvc.perform(post("/api/chats/{roomId}/read", roomId).cookie(otherCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/chats/unread-count").cookie(otherCookie))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    @DisplayName("상대가 읽으면 내 메시지에 읽음 표시가 붙는다")
    void 읽음_표시() throws Exception {
        Long roomId = openRoom();
        send(roomId, myCookie, "읽었니");

        // 상대가 읽기 전에는 읽음 표시가 없다
        mockMvc.perform(get("/api/chats/{roomId}/messages", roomId).cookie(myCookie))
                .andExpect(jsonPath("$.messages[0].read").value(false));

        mockMvc.perform(post("/api/chats/{roomId}/read", roomId).cookie(otherCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/chats/{roomId}/messages", roomId).cookie(myCookie))
                .andExpect(jsonPath("$.messages[0].read").value(true));
    }

    @Test
    @DisplayName("방 목록에 마지막 메시지와 안읽음 수가 함께 나온다")
    void 방_목록() throws Exception {
        Long roomId = openRoom();
        send(roomId, myCookie, "첫 메시지");
        send(roomId, myCookie, "마지막 메시지");

        mockMvc.perform(get("/api/chats").cookie(otherCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rooms[0].partnerUsername").value("tester1"))
                .andExpect(jsonPath("$.rooms[0].lastMessage").value("마지막 메시지"))
                .andExpect(jsonPath("$.rooms[0].unreadCount").value(2));
    }
}

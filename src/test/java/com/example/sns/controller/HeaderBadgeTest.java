package com.example.sns.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sns.entity.Notification;
import com.example.sns.entity.User;
import com.example.sns.repository.NotificationRepository;
import com.example.sns.repository.UserRepository;
import com.example.sns.service.ChatService;
import com.example.sns.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 헤더 배지 숫자 테스트.
 *
 * 배지 숫자는 페이지를 그릴 때 헤더에 같이 넣고, 화면은 따로 요청하지 않는다.
 * 헤더가 없는 부분 요청(무한 스크롤 조각)에는 배지가 없어야 숫자를 세지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HeaderBadgeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ChatService chatService;

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    @DisplayName("페이지를 그릴 때 헤더에 안 읽은 알림과 DM 수가 들어간다")
    void 헤더에_배지_숫자가_들어간다() throws Exception {
        User me = createUser("me");
        User other = createUser("other");
        for (int i = 0; i < 2; i++) {
            Notification notification = new Notification();
            notification.setRecipient(me);
            notification.setActor(other);
            notification.setType(Notification.Type.FOLLOW);
            notificationRepository.save(notification);
        }
        Long roomId = chatService.openRoom(other.getId(), me.getId());
        chatService.sendMessage(other.getId(), roomId, "안녕");

        String page = mockMvc.perform(get("/").cookie(cookieOf(me)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String fragment = mockMvc.perform(get("/feed").cookie(cookieOf(me)))
                .andReturn().getResponse().getContentAsString();

        assertThat(countOf(page, "notiBadge")).isEqualTo("2");
        assertThat(countOf(page, "chatBadge")).isEqualTo("1");
        assertThat(fragment).doesNotContain("notiBadge").doesNotContain("chatBadge");
    }

    /** id가 badgeId인 배지 태그의 data-count 값 */
    private String countOf(String html, String badgeId) {
        Matcher tag = Pattern.compile("<span[^>]*id=\"" + badgeId + "\"[^>]*>").matcher(html);
        assertThat(tag.find()).as("%s 배지가 없다", badgeId).isTrue();
        Matcher count = Pattern.compile("data-count=\"(\\d+)\"").matcher(tag.group());
        assertThat(count.find()).as("%s 배지에 숫자가 없다", badgeId).isTrue();
        return count.group(1);
    }

    private Cookie cookieOf(User user) {
        return new Cookie("JWT_TOKEN", jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole().name()));
    }

    private User createUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("password");
        user.setRole(User.Role.USER);
        return userRepository.save(user);
    }
}

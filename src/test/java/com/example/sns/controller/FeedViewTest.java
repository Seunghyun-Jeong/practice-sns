package com.example.sns.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sns.entity.Post;
import com.example.sns.entity.User;
import com.example.sns.repository.PostRepository;
import com.example.sns.repository.UserRepository;
import java.time.LocalDateTime;
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
 * 피드 화면 테스트.
 *
 * 첫 화면이 그린 마지막 카드의 data-created-at, data-post-id를 그대로 넘겨 다음 묶음을 받는다.
 * 화면이 하는 그대로 HTML에서 값을 꺼내 요청해서, 카드에 심은 값과 서버가 읽는 형식이 맞는지 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FeedViewTest {

    /** 카드의 여는 태그. 속성 순서는 템플릿 엔진이 정하므로 태그를 먼저 찾고 값은 따로 꺼낸다 */
    private static final Pattern CARD = Pattern.compile("<div[^>]*class=\"post-summary\"[^>]*>");
    private static final Pattern POST_ID = Pattern.compile("data-post-id=\"(\\d+)\"");
    private static final Pattern CREATED_AT = Pattern.compile("data-created-at=\"([^\"]+)\"");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Test
    @DisplayName("첫 화면 마지막 카드의 값을 넘기면 다음 묶음을 받는다")
    void 마지막_카드_다음부터_받는다() throws Exception {
        User writer = new User();
        writer.setUsername("writer");
        writer.setPassword("password");
        writer.setRole(User.Role.USER);
        writer = userRepository.save(writer);

        LocalDateTime base = LocalDateTime.now();
        for (int i = 0; i < 12; i++) {
            Post post = new Post();
            post.setAuthor(writer);
            post.setContent("post " + i);
            post.setImageUrl("/uploads/test.png");
            post.setCreatedAt(base.plusSeconds(i));
            postRepository.save(post);
        }

        String main = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Matcher card = CARD.matcher(main);
        String lastId = null;
        String lastAt = null;
        int cards = 0;
        while (card.find()) {
            cards++;
            lastId = valueOf(POST_ID, card.group());
            lastAt = valueOf(CREATED_AT, card.group());
        }

        String next = mockMvc.perform(get("/feed")
                        .param("beforeAt", lastAt)
                        .param("beforeId", lastId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(cards).isEqualTo(10);
        assertThat(CARD.matcher(next).results().count()).isEqualTo(2);
        assertThat(next).contains("data-has-next=\"false\"");
    }

    private String valueOf(Pattern attribute, String tag) {
        Matcher matcher = attribute.matcher(tag);
        assertThat(matcher.find()).as("카드에 %s 가 없다", attribute.pattern()).isTrue();
        return matcher.group(1);
    }
}

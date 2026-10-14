package com.example.sns.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sns.entity.Post;
import com.example.sns.entity.User;
import com.example.sns.repository.PostRepository;
import com.example.sns.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로필 게시글 테스트.
 *
 * 프로필은 그 사람의 글을 전부 그리지 않고 12장(한 줄 3장씩 4줄)만 그린 뒤, 끝에 닿으면 이어 받는다.
 * 게시물 수는 목록 길이가 아니라 따로 센 전체 수여야 하고, 글이 늘어도 프로필을 여는 쿼리 수는 같아야 한다.
 */
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN"
})
@AutoConfigureMockMvc
@Transactional
class ProfilePageTest {

    private static final Pattern CARD = Pattern.compile("<div[^>]*class=\"post-summary\"[^>]*>");
    private static final Pattern POST_ID = Pattern.compile("data-post-id=\"(\\d+)\"");
    private static final Pattern CREATED_AT = Pattern.compile("data-created-at=\"([^\"]+)\"");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("프로필은 12장만 그리고 게시물 수는 전체를 세며, 마지막 카드 다음부터 그 사람의 글만 이어 받는다")
    void 열두_장씩_이어_받는다() throws Exception {
        User writer = createUser("writer");
        User other = createUser("other");
        writePosts(writer, 15);
        writePosts(other, 5);

        String profile = mockMvc.perform(get("/profile/{id}", writer.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Matcher card = CARD.matcher(profile);
        String lastTag = null;
        int cards = 0;
        while (card.find()) {
            cards++;
            lastTag = card.group();
        }

        String next = mockMvc.perform(get("/feed")
                        .param("author", String.valueOf(writer.getId()))
                        .param("beforeAt", valueOf(CREATED_AT, lastTag))
                        .param("beforeId", valueOf(POST_ID, lastTag)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(cards).isEqualTo(12);
        assertThat(profile).contains("<b>15</b> 게시물");
        assertThat(CARD.matcher(next).results().count()).isEqualTo(3);
        assertThat(next).doesNotContain("other").contains("data-has-next=\"false\"");
    }

    @Test
    @DisplayName("글이 늘어도 프로필을 여는 쿼리 수가 같다")
    void 글_수와_관계없이_쿼리_수가_같다() throws Exception {
        long few = countProfileQueries("few", 13);
        long many = countProfileQueries("many", 60);

        assertThat(many)
                .as("글 13개일 때 %d회, 60개일 때 %d회", few, many)
                .isEqualTo(few);
    }

    private long countProfileQueries(String username, int postCount) throws Exception {
        User writer = createUser(username);
        writePosts(writer, postCount);
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        mockMvc.perform(get("/profile/{id}", writer.getId())).andExpect(status().isOk());
        return statistics.getPrepareStatementCount();
    }

    private void writePosts(User author, int count) {
        LocalDateTime base = LocalDateTime.now().minusDays(1);
        for (int i = 0; i < count; i++) {
            Post post = new Post();
            post.setAuthor(author);
            post.setContent(author.getUsername() + " " + i);
            post.setImageUrl("/uploads/test.png");
            post.setCreatedAt(base.plusMinutes(i));
            postRepository.save(post);
        }
    }

    private String valueOf(Pattern attribute, String tag) {
        Matcher matcher = attribute.matcher(tag);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private User createUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("password");
        user.setRole(User.Role.USER);
        return userRepository.save(user);
    }
}

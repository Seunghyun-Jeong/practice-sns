package com.example.sns.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.sns.entity.Post;
import com.example.sns.entity.User;
import com.example.sns.repository.PostHashtagRepository;
import com.example.sns.repository.PostRepository;
import com.example.sns.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시글 태그 연결 테스트.
 *
 * 글을 고치면 지금 연결과 비교해 사라진 태그만 떼고 새 태그만 붙인다. 태그를 그대로 두고 본문만 고치면
 * 연결을 건드리지 않아야 하고, 새 태그는 태그마다 찾지 않고 한 번에 찾아야 한다.
 */
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN"
})
@Transactional
class HashtagSyncTest {

    private static final String TEN_TAGS = "#t0 #t1 #t2 #t3 #t4 #t5 #t6 #t7 #t8 #t9";

    @Autowired
    private HashtagService hashtagService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostHashtagRepository postHashtagRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private User writer;

    @BeforeEach
    void setUp() {
        writer = new User();
        writer.setUsername("writer");
        writer.setPassword("password");
        writer.setRole(User.Role.USER);
        writer = userRepository.save(writer);
    }

    @Test
    @DisplayName("글을 고치면 사라진 태그만 떼고 새 태그만 붙인다")
    void 바뀐_태그만_고친다() {
        Post post = createPost("#spring #java #jpa 첫 글");
        hashtagService.syncTags(post);

        post.setContent("#spring #jpa #kotlin 고친 글");
        hashtagService.syncTags(post);
        flushAndClear();

        assertThat(tagNamesOf(post)).containsExactlyInAnyOrder("spring", "jpa", "kotlin");
    }

    @Test
    @DisplayName("태그는 그대로 두고 본문만 고치면 연결을 건드리지 않는다")
    void 태그가_그대로면_쓰지_않는다() {
        Post post = createPost(TEN_TAGS + " 첫 글");
        hashtagService.syncTags(post);

        post.setContent(TEN_TAGS + " 본문만 고친 글");
        long queries = countSyncQueries(post);

        assertThat(queries).as("지금 연결을 읽는 1회만 나가야 한다").isEqualTo(1);
    }

    @Test
    @DisplayName("이미 있는 태그 10개를 붙일 때 태그마다 찾지 않는다")
    void 있는_태그는_한_번에_찾는다() {
        hashtagService.syncTags(createPost(TEN_TAGS));

        long queries = countSyncQueries(createPost(TEN_TAGS));

        // 지금 연결 읽기 1 + 태그 한 번에 찾기 1 + 연결 저장 10
        assertThat(queries).isEqualTo(12);
    }

    private long countSyncQueries(Post post) {
        entityManager.flush();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        hashtagService.syncTags(post);
        entityManager.flush();
        return statistics.getPrepareStatementCount();
    }

    private List<String> tagNamesOf(Post post) {
        return postHashtagRepository.findTagNamesByPostIds(List.of(post.getId())).stream()
                .map(row -> (String) row[1])
                .toList();
    }

    private Post createPost(String content) {
        Post post = new Post();
        post.setAuthor(writer);
        post.setContent(content);
        post.setImageUrl("/uploads/test.png");
        return postRepository.save(post);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}

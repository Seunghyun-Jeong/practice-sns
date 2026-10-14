package com.example.sns.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.sns.dto.FeedPageDto;
import com.example.sns.dto.PostSummaryDto;
import com.example.sns.entity.Follow;
import com.example.sns.entity.Post;
import com.example.sns.entity.User;
import com.example.sns.repository.FollowRepository;
import com.example.sns.repository.PostRepository;
import com.example.sns.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
 * 피드 묶음 테스트.
 *
 * 피드는 보는 사이 새 글이 가장 자주 올라오는 목록이다. 페이지 번호로 읽으면 새 글 하나에 한 칸씩 밀려
 * 이미 본 글이 다음 묶음에 다시 나온다. 다음 묶음은 받은 마지막 글의 (작성 시각, id) 다음부터라서
 * 새 글이 올라와도, 시각이 같은 글이 묶음 경계에 걸려도 겹치거나 빠지면 안 된다.
 * 팔로잉 피드는 팔로우 목록을 자바로 가져오지 않으므로, 팔로우가 늘어도 쿼리 수가 같아야 한다.
 */
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN"
})
@Transactional
class FeedPageTest {

    @Autowired
    private PostService postService;

    @Autowired
    private HashtagService hashtagService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private User writer;

    @BeforeEach
    void setUp() {
        writer = createUser("writer");
    }

    @Test
    @DisplayName("보는 사이 새 글이 올라와도 다음 묶음에 이미 본 글이 다시 나오지 않는다")
    void 새_글이_올라와도_밀리지_않는다() {
        LocalDateTime base = LocalDateTime.now().minusHours(1);
        for (int i = 0; i < 25; i++) {
            createPost(writer, "old " + i, base.plusMinutes(i));
        }
        flushAndClear();

        FeedPageDto first = postService.getFeedPage(null, null, null);
        createPost(writer, "new while reading", LocalDateTime.now());
        flushAndClear();
        PostSummaryDto last = lastOf(first);
        FeedPageDto second = postService.getFeedPage(null, LocalDateTime.parse(last.getCreatedAt()), last.getId());

        assertThat(first.getPosts()).extracting(PostSummaryDto::getContent).endsWith("old 15");
        assertThat(second.getPosts()).extracting(PostSummaryDto::getContent).startsWith("old 14");
        assertThat(ids(second)).doesNotContainAnyElementsOf(ids(first));
    }

    @Test
    @DisplayName("시각이 같은 글이 묶음 경계에 걸려도 겹치거나 빠지지 않는다")
    void 시각이_같아도_겹치지도_빠지지도_않는다() {
        LocalDateTime sameTime = LocalDateTime.now();
        List<Long> written = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            written.add(createPost(writer, "post " + i, sameTime).getId());
        }
        flushAndClear();

        List<Long> received = new ArrayList<>();
        FeedPageDto page = postService.getFeedPage(null, null, null);
        received.addAll(ids(page));
        while (page.isHasNext()) {
            PostSummaryDto last = lastOf(page);
            page = postService.getFeedPage(null, LocalDateTime.parse(last.getCreatedAt()), last.getId());
            received.addAll(ids(page));
        }

        assertThat(received).containsExactlyInAnyOrderElementsOf(written).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("팔로잉 피드에는 내 글과 내가 팔로우한 사람의 글만 나온다")
    void 팔로잉_피드에는_팔로우한_사람과_내_글만_나온다() {
        User me = createUser("me");
        User followed = createUser("followed");
        User stranger = createUser("stranger");
        follow(me, followed);
        LocalDateTime now = LocalDateTime.now();
        createPost(me, "mine", now);
        createPost(followed, "followed", now.plusSeconds(1));
        createPost(stranger, "stranger", now.plusSeconds(2));
        flushAndClear();

        FeedPageDto page = postService.getFollowingFeedPage(me.getId(), null, null);

        assertThat(page.getPosts()).extracting(PostSummaryDto::getContent).containsExactly("followed", "mine");
    }

    @Test
    @DisplayName("팔로잉 피드는 팔로우가 늘어도 쿼리 수가 같다")
    void 팔로잉_피드_쿼리_수는_팔로우_수와_관계없다() {
        long few = countFollowingFeedQueries(2);
        long many = countFollowingFeedQueries(30);

        assertThat(many)
                .as("팔로우 2명일 때 %d회, 30명일 때 %d회", few, many)
                .isEqualTo(few);
    }

    @Test
    @DisplayName("해시태그 피드도 받은 마지막 글 다음부터 이어서 받는다")
    void 해시태그_피드를_이어서_받는다() {
        LocalDateTime base = LocalDateTime.now();
        for (int i = 0; i < 12; i++) {
            hashtagService.syncTags(createPost(writer, "#spring " + i, base.plusSeconds(i)));
        }
        createPost(writer, "태그 없음", base.plusSeconds(20));
        flushAndClear();

        FeedPageDto first = postService.getFeedPageByTag("spring", null, null, null);
        PostSummaryDto last = lastOf(first);
        FeedPageDto second = postService.getFeedPageByTag("spring", null,
                LocalDateTime.parse(last.getCreatedAt()), last.getId());

        assertThat(first.getPosts()).hasSize(10);
        assertThat(second.getPosts()).hasSize(2);
        assertThat(second.isHasNext()).isFalse();
    }

    /** n명을 팔로우하는 사람을 만들고(각자 글 하나), 팔로잉 피드 첫 묶음에 나간 쿼리 수를 센다 */
    private long countFollowingFeedQueries(int n) {
        User me = createUser("m" + n);
        for (int i = 0; i < n; i++) {
            User other = createUser("o" + n + "_" + i);
            follow(me, other);
            createPost(other, "글", LocalDateTime.now());
        }
        flushAndClear();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        postService.getFollowingFeedPage(me.getId(), null, null);
        return statistics.getPrepareStatementCount();
    }

    private PostSummaryDto lastOf(FeedPageDto page) {
        return page.getPosts().get(page.getPosts().size() - 1);
    }

    private List<Long> ids(FeedPageDto page) {
        return page.getPosts().stream().map(PostSummaryDto::getId).toList();
    }

    private Post createPost(User author, String content, LocalDateTime createdAt) {
        Post post = new Post();
        post.setAuthor(author);
        post.setContent(content);
        post.setImageUrl("/uploads/test.png");
        post.setCreatedAt(createdAt);
        return postRepository.save(post);
    }

    private void follow(User follower, User following) {
        Follow follow = new Follow();
        follow.setFollower(follower);
        follow.setFollowing(following);
        followRepository.save(follow);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private User createUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("password");
        user.setRole(User.Role.USER);
        return userRepository.save(user);
    }
}

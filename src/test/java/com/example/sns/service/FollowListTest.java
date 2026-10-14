package com.example.sns.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.sns.dto.FollowPageDto;
import com.example.sns.dto.FollowUserDto;
import com.example.sns.entity.Follow;
import com.example.sns.entity.User;
import com.example.sns.repository.FollowRepository;
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
 * 팔로워 / 팔로잉 목록 테스트.
 *
 * 사람마다 따로 조회하면 팔로워 수만큼 쿼리가 늘어나므로, 적을 때와 많을 때 쿼리 수가 같은지 본다.
 * 정지된 사람은 쿼리에서 걸러야 한 묶음이 20명으로 꽉 찬다. 자바에서 거르면 그만큼 비어서 나간다.
 * 다음 묶음은 (팔로우한 시각, follow id) 다음부터라서, 시각이 같아도 겹치거나 빠지면 안 된다.
 */
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN"
})
@Transactional
class FollowListTest {

    @Autowired
    private FollowService followService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private User target;

    @BeforeEach
    void setUp() {
        target = createUser("target");
    }

    @Test
    @DisplayName("팔로워가 늘어도 목록 조회 쿼리 수가 같다")
    void 팔로워_수와_관계없이_쿼리_수가_같다() {
        long few = countFollowerQueries(2);
        long many = countFollowerQueries(30);

        assertThat(many)
                .as("팔로워 2명일 때 %d회, 30명일 때 %d회", few, many)
                .isEqualTo(few);
    }

    @Test
    @DisplayName("정지된 사람이 섞여 있어도 한 묶음은 20명으로 차고, 시각이 같아도 다음 묶음과 겹치거나 빠지지 않는다")
    void 정지된_사람을_빼고_20명씩_받는다() {
        LocalDateTime sameTime = LocalDateTime.now();
        List<Long> visible = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            User follower = createUser("f" + i);
            if (i % 8 == 0) {
                follower.setSuspendedUntil(LocalDateTime.now().plusDays(1));   // 4명 정지
            } else {
                visible.add(follower.getId());
            }
            follow(follower, target, sameTime);
        }
        flushAndClear();

        FollowPageDto first = followService.getFollowers(target.getId(), null, null);
        FollowUserDto last = first.getUsers().get(first.getUsers().size() - 1);
        FollowPageDto second = followService.getFollowers(target.getId(), last.getFollowedAt(), last.getFollowId());

        assertThat(first.getUsers()).hasSize(20);
        assertThat(first.isHasNext()).isTrue();
        assertThat(second.getUsers()).hasSize(1);
        assertThat(second.isHasNext()).isFalse();

        List<Long> received = new ArrayList<>();
        first.getUsers().forEach(u -> received.add(u.getId()));
        second.getUsers().forEach(u -> received.add(u.getId()));
        assertThat(received).containsExactlyInAnyOrderElementsOf(visible).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("팔로잉 목록은 최근에 팔로우한 사람이 먼저 나온다")
    void 팔로잉은_최근에_팔로우한_순이다() {
        LocalDateTime base = LocalDateTime.now();
        User older = createUser("older");
        User newer = createUser("newer");
        follow(target, older, base);
        follow(target, newer, base.plusSeconds(1));
        flushAndClear();

        FollowPageDto page = followService.getFollowingList(target.getId(), null, null);

        assertThat(page.getUsers()).extracting(FollowUserDto::getUsername).containsExactly("newer", "older");
    }

    /** 팔로워가 n명인 사람을 만들고, 팔로워 목록 첫 묶음에 나간 쿼리 수를 센다 */
    private long countFollowerQueries(int n) {
        User user = createUser("u" + n);
        for (int i = 0; i < n; i++) {
            follow(createUser("f" + n + "_" + i), user, LocalDateTime.now());
        }
        flushAndClear();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        followService.getFollowers(user.getId(), null, null);
        return statistics.getPrepareStatementCount();
    }

    private void follow(User follower, User following, LocalDateTime followedAt) {
        Follow follow = new Follow();
        follow.setFollower(follower);
        follow.setFollowing(following);
        follow.setFollowedAt(followedAt);
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

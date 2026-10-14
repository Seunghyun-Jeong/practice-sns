package com.example.sns.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.sns.dto.FeedQuery;
import com.example.sns.entity.Follow;
import com.example.sns.entity.Post;
import com.example.sns.entity.User;
import com.example.sns.repository.FollowRepository;
import com.example.sns.repository.PostRepository;
import com.example.sns.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 새 글 확인 테스트.
 *
 * 화면 맨 위 글보다 나중에 쓴 글이 "그 피드에 보일 글"일 때만 있다고 답해야 한다.
 * 피드에는 안 보이는 글(정지된 사람, 팔로우하지 않은 사람, 태그 없는 글) 때문에 버튼이 뜨면 안 된다.
 */
@SpringBootTest
@Transactional
class NewPostCheckTest {

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

    private User me;
    private User followed;
    private User stranger;
    private Post top;   // 화면 맨 위에 있는 글

    @BeforeEach
    void setUp() {
        me = createUser("me");
        followed = createUser("followed");
        stranger = createUser("stranger");
        Follow follow = new Follow();
        follow.setFollower(me);
        follow.setFollowing(followed);
        followRepository.save(follow);

        top = createPost(followed, "#spring 맨 위", LocalDateTime.now().minusMinutes(1));
        hashtagService.syncTags(top);
    }

    @Test
    @DisplayName("맨 위 글 뒤로 새 글이 없으면 없다고 답한다")
    void 새_글이_없으면_없다() {
        flush();
        assertThat(hasNew(FeedQuery.of("all", null, me.getId()))).isFalse();
        assertThat(hasNew(FeedQuery.of("following", null, me.getId()))).isFalse();
        assertThat(hasNew(FeedQuery.of(null, "spring", me.getId()))).isFalse();
    }

    @Test
    @DisplayName("전체 피드는 누구 글이든 새 글이 있으면 있다고 답하지만, 정지된 사람의 글은 세지 않는다")
    void 전체_피드_새_글() {
        User suspended = createUser("banned");
        suspended.setSuspendedUntil(LocalDateTime.now().plusDays(1));
        createPost(suspended, "정지된 사람의 새 글", LocalDateTime.now());
        flush();
        assertThat(hasNew(FeedQuery.of("all", null, me.getId()))).isFalse();

        createPost(stranger, "새 글", LocalDateTime.now());
        flush();
        assertThat(hasNew(FeedQuery.of("all", null, me.getId()))).isTrue();
    }

    @Test
    @DisplayName("팔로잉 피드는 팔로우한 사람의 새 글만 센다")
    void 팔로잉_피드_새_글() {
        createPost(stranger, "모르는 사람의 새 글", LocalDateTime.now());
        flush();
        assertThat(hasNew(FeedQuery.of("following", null, me.getId()))).isFalse();

        createPost(followed, "팔로우한 사람의 새 글", LocalDateTime.now());
        flush();
        assertThat(hasNew(FeedQuery.of("following", null, me.getId()))).isTrue();
    }

    @Test
    @DisplayName("해시태그 피드는 그 태그가 달린 새 글만 센다")
    void 해시태그_피드_새_글() {
        createPost(stranger, "태그 없는 새 글", LocalDateTime.now());
        flush();
        assertThat(hasNew(FeedQuery.of(null, "spring", me.getId()))).isFalse();

        hashtagService.syncTags(createPost(stranger, "#spring 새 글", LocalDateTime.now()));
        flush();
        assertThat(hasNew(FeedQuery.of(null, "spring", me.getId()))).isTrue();
    }

    @Test
    @DisplayName("화면이 비어 있으면 보여줄 글이 하나라도 있는지를 본다")
    void 빈_화면이면_글이_있는지_본다() {
        flush();
        assertThat(postService.hasNewPosts(FeedQuery.of("following", null, me.getId()), null, null)).isTrue();
        assertThat(postService.hasNewPosts(FeedQuery.of(null, "kotlin", me.getId()), null, null)).isFalse();
    }

    private boolean hasNew(FeedQuery query) {
        return postService.hasNewPosts(query, top.getCreatedAt(), top.getId());
    }

    private Post createPost(User author, String content, LocalDateTime createdAt) {
        Post post = new Post();
        post.setAuthor(author);
        post.setContent(content);
        post.setImageUrl("/uploads/test.png");
        post.setCreatedAt(createdAt);
        return postRepository.save(post);
    }

    private void flush() {
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

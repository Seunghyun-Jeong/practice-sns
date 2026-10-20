package com.example.sns.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.example.sns.entity.Notification;
import com.example.sns.entity.Post;
import com.example.sns.entity.User;
import com.example.sns.event.NotificationEvent;
import com.example.sns.repository.NotificationRepository;
import com.example.sns.repository.PostLikeRepository;
import com.example.sns.repository.PostRepository;
import com.example.sns.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 알림이 좋아요의 커밋 뒤에 따로 처리되는지 확인한다.
 *
 * 커밋이 있어야 이벤트가 처리되므로 이 테스트는 트랜잭션으로 감싸지 않는다.
 * 대신 만든 데이터를 끝나고 직접 지운다.
 */
@SpringBootTest
class NotificationEventTest {

    @Autowired
    private PostLikeService postLikeService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostLikeRepository postLikeRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoSpyBean
    private NotificationService notificationService;

    private User author;
    private User liker;
    private Post post;

    @BeforeEach
    void setUp() {
        author = createUser("evauthor");
        liker = createUser("evliker");

        post = new Post();
        post.setAuthor(author);
        post.setContent("글");
        post.setImageUrl("/uploads/test.png");
        post.setCreatedAt(LocalDateTime.now());
        postRepository.save(post);
    }

    @AfterEach
    void tearDown() {
        // 한 트랜잭션 안에서 다시 읽어 지운다. 밖에서 읽은 엔티티를 지우면 이미 닫힌 컨텍스트의 것이라 실패한다
        transactionTemplate.executeWithoutResult(status -> {
            notificationRepository.deleteAll(likeNotifications());
            postRepository.deleteById(post.getId());
            userRepository.deleteById(liker.getId());
            userRepository.deleteById(author.getId());
        });
    }

    @Test
    @DisplayName("좋아요가 커밋되면 글쓴이에게 알림이 생긴다")
    void 커밋_뒤에_알림이_생긴다() {
        postLikeService.toggleLike(post.getId(), liker.getUsername());

        assertThat(likeNotifications()).hasSize(1);
    }

    @Test
    @DisplayName("좋아요가 롤백되면 알림도 생기지 않는다")
    void 롤백되면_알림이_없다() {
        transactionTemplate.executeWithoutResult(status -> {
            postLikeService.toggleLike(post.getId(), liker.getUsername());
            status.setRollbackOnly();
        });

        assertThat(postLikeRepository.existsByPostAndUser(post, liker)).isFalse();
        assertThat(likeNotifications()).isEmpty();
    }

    @Test
    @DisplayName("알림 처리가 실패해도 좋아요는 남고 요청은 성공한다")
    void 알림이_실패해도_좋아요는_남는다() {
        doThrow(new IllegalStateException("알림 저장 실패")).when(notificationService).apply(any(NotificationEvent.class));

        boolean liked = postLikeService.toggleLike(post.getId(), liker.getUsername());

        assertThat(liked).isTrue();
        assertThat(postLikeRepository.existsByPostAndUser(post, liker)).isTrue();
        assertThat(likeNotifications()).isEmpty();
    }

    @Test
    @DisplayName("좋아요를 취소하면 커밋 뒤에 알림도 지워진다")
    void 취소하면_알림이_지워진다() {
        postLikeService.toggleLike(post.getId(), liker.getUsername());
        postLikeService.toggleLike(post.getId(), liker.getUsername());

        assertThat(likeNotifications()).isEmpty();
    }

    private List<Notification> likeNotifications() {
        return notificationRepository.findByRecipientAndActorAndTypeAndPost(author, liker, Notification.Type.POST_LIKE, post);
    }

    private User createUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("password");
        user.setRole(User.Role.USER);
        return userRepository.save(user);
    }
}

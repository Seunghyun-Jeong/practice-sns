package com.example.sns.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

import com.example.sns.entity.Notification;
import com.example.sns.entity.NotificationOutbox;
import com.example.sns.entity.Post;
import com.example.sns.entity.User;
import com.example.sns.event.NotificationEvent;
import com.example.sns.event.NotificationOutboxRelay;
import com.example.sns.repository.NotificationOutboxRepository;
import com.example.sns.repository.NotificationRepository;
import com.example.sns.repository.PostLikeRepository;
import com.example.sns.repository.PostRepository;
import com.example.sns.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 알림 이벤트가 같은 트랜잭션에서 Outbox에 적히고, 릴레이가 나중에 순서대로 반영하는지 확인한다.
 *
 * 커밋이 있어야 Outbox 줄이 보이므로 이 테스트는 트랜잭션으로 감싸지 않는다.
 * 대신 만든 데이터를 끝나고 직접 지운다. 스케줄러는 테스트 설정에서 꺼 두고 릴레이를 직접 부른다.
 */
@SpringBootTest
class NotificationOutboxTest {

    @Autowired
    private PostLikeService postLikeService;

    @Autowired
    private NotificationOutboxRelay relay;

    @Autowired
    private NotificationOutboxRepository outboxRepository;

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

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private LockProvider lockProvider;

    @MockitoSpyBean
    private NotificationService notificationService;

    private User author;
    private User other;
    private User liker;
    private Post post;
    private Post otherPost;

    @BeforeEach
    void setUp() {
        // 다른 테스트가 커밋하고 남긴 줄이 릴레이에 섞이지 않게 비우고 시작한다
        outboxRepository.deleteAllInBatch();

        author = createUser("obauthor");
        other = createUser("obother");
        liker = createUser("obliker");
        post = createPost(author);
        otherPost = createPost(other);
    }

    @AfterEach
    void tearDown() {
        // 한 트랜잭션 안에서 다시 읽어 지운다. 밖에서 읽은 엔티티를 지우면 이미 닫힌 컨텍스트의 것이라 실패한다
        transactionTemplate.executeWithoutResult(status -> {
            notificationRepository.deleteAll(likeNotifications(author, post));
            notificationRepository.deleteAll(likeNotifications(other, otherPost));
            postRepository.deleteById(post.getId());
            postRepository.deleteById(otherPost.getId());
            userRepository.deleteById(liker.getId());
            userRepository.deleteById(other.getId());
            userRepository.deleteById(author.getId());
        });
        outboxRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("좋아요가 커밋되면 Outbox에 한 줄이 남고, 알림은 릴레이가 반영한 뒤에 생긴다")
    void 커밋되면_Outbox에_남고_릴레이가_반영한다() {
        postLikeService.toggleLike(post.getId(), liker.getUsername());

        assertThat(outboxRepository.findAll()).hasSize(1);
        assertThat(likeNotifications(author, post)).isEmpty();

        assertThat(relay.relayPending()).isEqualTo(1);

        assertThat(likeNotifications(author, post)).hasSize(1);
        assertThat(outboxRepository.findAll()).allSatisfy(row -> assertThat(row.getProcessedAt()).isNotNull());
        assertThat(relay.relayPending()).isZero();   // 처리한 줄은 다시 반영하지 않는다
    }

    @Test
    @DisplayName("좋아요가 롤백되면 Outbox 줄도 남지 않는다")
    void 롤백되면_Outbox도_없다() {
        transactionTemplate.executeWithoutResult(status -> {
            postLikeService.toggleLike(post.getId(), liker.getUsername());
            status.setRollbackOnly();
        });

        assertThat(postLikeRepository.existsByPostAndUser(post, liker)).isFalse();
        assertThat(outboxRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("트랜잭션 밖에서 알림 이벤트를 발행하면 조용히 넘어가지 않고 예외가 난다")
    void 트랜잭션_밖_발행은_예외() {
        NotificationEvent event = NotificationEvent.created(author, liker, Notification.Type.POST_LIKE, post);

        assertThatThrownBy(() -> eventPublisher.publishEvent(event))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(outboxRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("반영이 실패해도 좋아요는 남고, 줄은 다음 차례에 다시 시도된다")
    void 실패하면_다음_차례에_다시_시도한다() {
        doThrow(new IllegalStateException("알림 저장 실패")).when(notificationService).apply(Mockito.any());

        boolean liked = postLikeService.toggleLike(post.getId(), liker.getUsername());
        relay.relayPending();

        assertThat(liked).isTrue();
        assertThat(postLikeRepository.existsByPostAndUser(post, liker)).isTrue();
        NotificationOutbox row = outboxRepository.findAll().get(0);
        assertThat(row.getProcessedAt()).isNull();
        assertThat(row.getAttempts()).isEqualTo(1);

        Mockito.reset(notificationService);
        relay.relayPending();

        assertThat(likeNotifications(author, post)).hasSize(1);
    }

    @Test
    @DisplayName("상한만큼 실패한 줄은 실패로 확정하고 더 시도하지 않는다")
    void 상한을_넘으면_실패로_확정한다() {
        doThrow(new IllegalStateException("알림 저장 실패")).when(notificationService).apply(Mockito.any());
        postLikeService.toggleLike(post.getId(), liker.getUsername());

        for (int i = 0; i < 10; i++) {
            relay.relayPending();
        }

        NotificationOutbox row = outboxRepository.findAll().get(0);
        assertThat(row.getAttempts()).isEqualTo(5);
        assertThat(row.getFailedAt()).isNotNull();
        assertThat(row.getLastError()).contains("알림 저장 실패");
    }

    @Test
    @DisplayName("한 사람의 반영이 실패하면 그 사람의 뒤 줄만 미루고, 다음 차례에 순서대로 반영한다")
    void 실패한_사람의_뒤_줄만_미룬다() {
        // author에게 가는 첫 좋아요만 한 번 실패시킨다
        doThrow(new IllegalStateException("일시 장애")).when(notificationService).apply(argThat(e -> e != null &&
                e.recipientId().equals(author.getId()) && e.action() == NotificationEvent.Action.CREATE));

        postLikeService.toggleLike(post.getId(), liker.getUsername());       // author: 좋아요
        postLikeService.toggleLike(post.getId(), liker.getUsername());       // author: 취소
        postLikeService.toggleLike(otherPost.getId(), liker.getUsername());  // other: 좋아요

        assertThat(relay.relayPending()).isEqualTo(1);   // other 것만 반영, author의 취소는 미룸
        assertThat(likeNotifications(other, otherPost)).hasSize(1);

        Mockito.reset(notificationService);
        assertThat(relay.relayPending()).isEqualTo(2);   // author의 좋아요, 취소가 순서대로

        // 취소가 먼저 반영됐다면 좋아요 알림이 남았을 것이다
        assertThat(likeNotifications(author, post)).isEmpty();
    }

    @Test
    @DisplayName("정리는 기준 시각 전에 처리된 줄만 지우고, 처리 안 된 줄과 실패한 줄은 남긴다")
    void 처리된_오래된_줄만_지운다() {
        postLikeService.toggleLike(post.getId(), liker.getUsername());
        relay.relayPending();
        postLikeService.toggleLike(otherPost.getId(), liker.getUsername());   // 아직 처리 안 됨

        int deleted = relay.deleteProcessedBefore(LocalDateTime.now().plusSeconds(1));

        assertThat(deleted).isEqualTo(1);
        assertThat(outboxRepository.findAll()).singleElement()
                .satisfies(row -> assertThat(row.getProcessedAt()).isNull());
    }

    @Test
    @DisplayName("ShedLock 잠금은 한 번에 한 쪽만 잡는다. 서버 두 대가 같은 차례를 돌지 않는 원리다")
    void 잠금은_한_쪽만_잡는다() {
        LockConfiguration config = new LockConfiguration(
                Instant.now(), "obTestLock", Duration.ofSeconds(30), Duration.ZERO);

        Optional<SimpleLock> server1 = lockProvider.lock(config);
        Optional<SimpleLock> server2 = lockProvider.lock(config);

        assertThat(server1).isPresent();
        assertThat(server2).isEmpty();

        server1.get().unlock();
        Optional<SimpleLock> afterRelease = lockProvider.lock(config);
        assertThat(afterRelease).isPresent();
        afterRelease.get().unlock();
    }

    private List<Notification> likeNotifications(User recipient, Post target) {
        return notificationRepository.findByRecipientAndActorAndTypeAndPost(recipient, liker, Notification.Type.POST_LIKE, target);
    }

    private Post createPost(User writer) {
        Post p = new Post();
        p.setAuthor(writer);
        p.setContent("글");
        p.setImageUrl("/uploads/test.png");
        p.setCreatedAt(LocalDateTime.now());
        return postRepository.save(p);
    }

    private User createUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("password");
        user.setRole(User.Role.USER);
        return userRepository.save(user);
    }
}

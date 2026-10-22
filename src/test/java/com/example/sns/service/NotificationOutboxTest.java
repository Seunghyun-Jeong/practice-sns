package com.example.sns.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;


import com.example.sns.entity.Notification;
import com.example.sns.entity.NotificationOutbox;
import com.example.sns.entity.Post;
import com.example.sns.entity.User;
import com.example.sns.event.NotificationEvent;
import com.example.sns.event.NotificationOutboxRelay;
import com.example.sns.event.NotificationTopic;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 알림 이벤트가 같은 트랜잭션에서 Outbox에 적히고, 릴레이가 Kafka로 보내 소비자가 순서대로 반영하는지 확인한다.
 *
 * 커밋이 있어야 Outbox 줄이 보이므로 이 테스트는 트랜잭션으로 감싸지 않는다.
 * 대신 만든 데이터를 끝나고 직접 지운다. 스케줄러는 테스트 설정에서 꺼 두고 릴레이를 직접 부른다.
 *
 * Kafka는 테스트 안에 띄우는 브로커(@EmbeddedKafka)를 쓴다. Docker 없이 ./gradlew test로 돈다.
 * 소비자는 따로 돌므로 반영 결과는 await로 기다려서 확인한다. verify(timeout)은 apply가 불린 순간 통과해서
 * 커밋 전에 읽을 수 있다. 기다리지 않고 끝내면 정리 중에 소비자가 늦게 반영해 지운 사용자를 가리키는 알림이 생길 수 있다.
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true"
})
// 토픽은 앱이 만들지 않으므로 docker-compose.yml과 같은 조건(자동 생성 끔, 파티션 3)으로 여기서 만든다
@EmbeddedKafka(topics = NotificationTopic.NAME, partitions = 3,
        brokerProperties = "auto.create.topics.enable=false")
class NotificationOutboxTest {

    private static final Duration CONSUME_TIMEOUT = Duration.ofSeconds(10);

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

    // Spring Boot가 KafkaTemplate<?, ?>로 등록해서 KafkaTemplate<String, String>으로는 감쌀 빈을 못 찾는다
    @SuppressWarnings("rawtypes")
    @MockitoSpyBean
    private KafkaTemplate kafkaTemplate;

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
    @DisplayName("좋아요가 커밋되면 Outbox에 한 줄이 남고, 릴레이가 Kafka로 보낸 뒤 소비자가 알림을 만든다")
    void 커밋되면_Outbox에_남고_Kafka를_거쳐_반영한다() {
        postLikeService.toggleLike(post.getId(), liker.getUsername());

        assertThat(outboxRepository.findAll()).hasSize(1);
        assertThat(likeNotifications(author, post)).isEmpty();

        assertThat(relay.relayPending()).isEqualTo(1);
        awaitConsumed(() -> assertThat(likeNotifications(author, post)).hasSize(1));

        assertThat(outboxRepository.findAll()).allSatisfy(row -> assertThat(row.getProcessedAt()).isNotNull());
        assertThat(relay.relayPending()).isZero();   // Kafka가 받은 줄은 다시 보내지 않는다
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
    @DisplayName("Kafka가 응답하지 않아도 좋아요는 남고, 줄은 시도 횟수를 늘리지 않은 채 다음 차례에 보내진다")
    void 브로커_장애는_횟수를_세지_않고_다시_보낸다() {
        // 브로커가 꺼져 있으면 KafkaTemplate은 실패한 결과를 돌려주지 않고 이렇게 던진다(실제 브로커를 끄고 확인한 모양)
        doThrow(new KafkaException("Send failed", new TimeoutException("Topic notification-events not present in metadata after 5000 ms.")))
                .when(kafkaTemplate).send(anyString(), anyString(), anyString());

        boolean liked = postLikeService.toggleLike(post.getId(), liker.getUsername());
        for (int i = 0; i < 10; i++) {
            assertThat(relay.relayPending()).isZero();
        }

        assertThat(liked).isTrue();
        assertThat(postLikeRepository.existsByPostAndUser(post, liker)).isTrue();
        NotificationOutbox row = outboxRepository.findAll().get(0);
        assertThat(row.getProcessedAt()).isNull();
        assertThat(row.getAttempts()).isZero();   // 셌다면 브로커가 잠깐 멈춘 사이에 실패로 확정됐을 것이다
        assertThat(row.getFailedAt()).isNull();

        Mockito.reset(kafkaTemplate);
        assertThat(relay.relayPending()).isEqualTo(1);
        awaitConsumed(() -> assertThat(likeNotifications(author, post)).hasSize(1));
    }

    @Test
    @DisplayName("줄 자체가 문제인 실패는 상한만큼 세고 실패로 확정해 더 보내지 않는다")
    void 줄_문제는_상한을_넘으면_실패로_확정한다() {
        doReturn(failed(new RecordTooLargeException("메시지가 너무 큼"))).when(kafkaTemplate).send(anyString(), anyString(), anyString());
        postLikeService.toggleLike(post.getId(), liker.getUsername());

        for (int i = 0; i < 10; i++) {
            relay.relayPending();
        }

        NotificationOutbox row = outboxRepository.findAll().get(0);
        assertThat(row.getAttempts()).isEqualTo(5);
        assertThat(row.getFailedAt()).isNotNull();
        assertThat(row.getLastError()).contains("메시지가 너무 큼");
    }

    @Test
    @DisplayName("한 사람의 전송이 실패하면 그 사람의 뒤 줄은 Kafka에 들어갔어도 다시 보내고, 소비자는 결국 순서대로 반영한다")
    void 실패한_사람의_뒤_줄은_다시_보낸다() {
        // author에게 가는 첫 메시지만 응답이 조금 늦게 실패로 온다. 나머지는 실제로 보낸다
        CompletableFuture<SendResult<String, String>> lateFailure = CompletableFuture.supplyAsync(() -> {
            throw new TimeoutException("일시 장애");
        }, CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS));
        doReturn(lateFailure).doCallRealMethod()
                .when(kafkaTemplate).send(eq(NotificationTopic.NAME), eq(author.getId().toString()), anyString());

        postLikeService.toggleLike(post.getId(), liker.getUsername());       // author: 좋아요 (실패)
        postLikeService.toggleLike(post.getId(), liker.getUsername());       // author: 취소 (Kafka에 들어감)
        postLikeService.toggleLike(otherPost.getId(), liker.getUsername());  // other: 좋아요

        assertThat(relay.relayPending()).isEqualTo(1);   // other 것만 처리됨, author의 취소는 들어갔어도 처리됨으로 적지 않음
        assertThat(outboxRepository.findAll()).filteredOn(row -> row.getProcessedAt() == null).hasSize(2);

        Mockito.reset(kafkaTemplate);
        assertThat(relay.relayPending()).isEqualTo(2);   // author의 좋아요, 취소를 순서대로 다시 보냄

        // 파티션에는 author의 취소, 좋아요, 취소가 차례로 있다. 소비자가 넷(other 포함)을 모두 집어 들길 기다린다.
        // 네 번째가 불렸다면 앞의 좋아요는 이미 반영됐으므로, 그 뒤로 알림이 비는 것은 마지막 취소 덕이다
        verify(notificationService, timeout(CONSUME_TIMEOUT.toMillis()).times(4)).apply(any());

        // 취소를 다시 보내지 않았다면 좋아요 알림이 남아 여기서 시간 초과로 실패한다
        awaitConsumed(() -> assertThat(likeNotifications(author, post)).isEmpty());
        awaitConsumed(() -> assertThat(likeNotifications(other, otherPost)).hasSize(1));
    }

    @Test
    @DisplayName("정리는 기준 시각 전에 처리된 줄만 지우고, 처리 안 된 줄과 실패한 줄은 남긴다")
    void 처리된_오래된_줄만_지운다() {
        postLikeService.toggleLike(post.getId(), liker.getUsername());
        relay.relayPending();
        awaitConsumed(() -> assertThat(likeNotifications(author, post)).hasSize(1));
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

    private void awaitConsumed(Runnable assertion) {
        await().atMost(CONSUME_TIMEOUT).untilAsserted(assertion::run);
    }

    private static CompletableFuture<SendResult<String, String>> failed(Throwable cause) {
        return CompletableFuture.failedFuture(cause);
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

package com.example.sns.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.sns.entity.Notification;
import com.example.sns.entity.Post;
import com.example.sns.entity.User;
import com.example.sns.repository.NotificationRepository;
import com.example.sns.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 알림을 만들지 말지 판단하는 규칙 테스트.
 * 팔로우·좋아요는 껐다 켜는 동작이라 중복을 막고, 댓글은 매번 알린다.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationService notificationService;

    private User author;
    private User actor;
    private Post post;

    @BeforeEach
    void setUp() {
        author = new User();
        author.setId(1L);

        actor = new User();
        actor.setId(2L);

        post = new Post();
        post.setId(10L);
        post.setAuthor(author);
    }

    @Test
    @DisplayName("남이 내 글에 좋아요를 누르면 알림을 만든다")
    void 좋아요_알림을_만든다() {
        when(notificationRepository.findByRecipientAndActorAndTypeAndPost(author, actor, Notification.Type.POST_LIKE, post))
                .thenReturn(List.of());

        notificationService.notify(author, actor, Notification.Type.POST_LIKE, post);

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    @DisplayName("내 행동으로 나에게는 알림이 오지 않는다")
    void 본인_행동은_알림이_없다() {
        notificationService.notify(author, author, Notification.Type.POST_LIKE, post);

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    @DisplayName("같은 좋아요 알림이 이미 있으면 다시 만들지 않는다")
    void 좋아요_알림은_중복되지_않는다() {
        when(notificationRepository.findByRecipientAndActorAndTypeAndPost(author, actor, Notification.Type.POST_LIKE, post))
                .thenReturn(List.of(new Notification()));

        notificationService.notify(author, actor, Notification.Type.POST_LIKE, post);

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    @DisplayName("댓글은 이미 알림이 있어도 매번 새로 만든다")
    void 댓글_알림은_매번_만든다() {
        notificationService.notify(author, actor, Notification.Type.COMMENT, post, null);
        notificationService.notify(author, actor, Notification.Type.COMMENT, post, null);

        verify(notificationRepository, times(2)).save(any(Notification.class));
    }

    @Test
    @DisplayName("팔로우를 취소하면 알림도 지운다")
    void 팔로우_취소하면_알림도_지운다() {
        Notification existing = new Notification();
        when(notificationRepository.findByRecipientAndActorAndTypeAndPostIsNull(author, actor, Notification.Type.FOLLOW))
                .thenReturn(List.of(existing));

        notificationService.cancel(author, actor, Notification.Type.FOLLOW, null);

        verify(notificationRepository).delete(existing);
    }
}

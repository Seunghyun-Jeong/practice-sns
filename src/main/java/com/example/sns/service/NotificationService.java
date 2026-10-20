package com.example.sns.service;

import com.example.sns.dto.NotificationDto;
import com.example.sns.entity.Comment;
import com.example.sns.entity.Notification;
import com.example.sns.entity.Post;
import com.example.sns.entity.User;
import com.example.sns.event.NotificationEvent;
import com.example.sns.repository.CommentRepository;
import com.example.sns.repository.NotificationRepository;
import com.example.sns.repository.PostRepository;
import com.example.sns.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final BadgeService badgeService;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    /** 드롭다운에 보여줄 최근 알림 개수 */
    private static final int RECENT_SIZE = 20;

    /**
     * 알림 이벤트를 반영한다. 알림을 만들고 지우는 입구는 이것 하나다.
     * 지금은 커밋 뒤 리스너가 부르고, Kafka를 붙이면 소비자가 같은 메서드를 부른다.
     *
     * id로 프록시만 만들고 엔티티를 읽지 않는다. 알림 저장과 중복 확인에는 id만 있으면 된다.
     */
    @Transactional
    public void apply(NotificationEvent event) {
        User recipient = event.recipientId() != null ? userRepository.getReferenceById(event.recipientId()) : null;
        User actor = event.actorId() != null ? userRepository.getReferenceById(event.actorId()) : null;
        Post post = event.postId() != null ? postRepository.getReferenceById(event.postId()) : null;
        Comment comment = event.commentId() != null ? commentRepository.getReferenceById(event.commentId()) : null;

        if (event.action() == NotificationEvent.Action.CANCEL) {
            cancel(recipient, actor, event.type(), post);
        } else {
            notify(recipient, actor, event.type(), post, comment);
        }
    }

    /**
     * 알림을 만든다.
     * 본인이 자기 글에 한 행동은 알림을 만들지 않고,
     * 같은 알림이 이미 있으면 다시 만들지 않는다.
     */
    void notify(User recipient, User actor, Notification.Type type, Post post) {
        notify(recipient, actor, type, post, null);
    }

    /** 댓글 알림처럼 어떤 댓글인지까지 함께 남겨야 하는 경우 */
    void notify(User recipient, User actor, Notification.Type type, Post post, Comment comment) {
        if (recipient == null || actor == null) {
            return;
        }
        if (recipient.getId().equals(actor.getId())) {
            return;   // 내 행동으로 나에게 알림이 오지 않게
        }

        if (dedupes(type) && findExisting(recipient, actor, type, post).isPresent()) {
            return;
        }

        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setActor(actor);
        notification.setType(type);
        notification.setPost(post);
        notification.setComment(comment);
        notificationRepository.save(notification);

        // 받는 사람이 접속 중이면 종 아이콘 배지가 바로 갱신되도록 밀어준다
        badgeService.pushNotificationBadge(recipient.getId());
    }

    /** 행동을 취소했을 때 (좋아요 취소, 언팔로우) 알림도 지운다 */
    void cancel(User recipient, User actor, Notification.Type type, Post post) {
        if (recipient == null || actor == null || recipient.getId().equals(actor.getId())) {
            return;
        }
        findExisting(recipient, actor, type, post).ifPresent(n -> {
            notificationRepository.delete(n);
            badgeService.pushNotificationBadge(recipient.getId());   // 취소로 줄어든 배지도 바로 반영
        });
    }

    private Optional<Notification> findExisting(User recipient, User actor, Notification.Type type, Post post) {
        List<Notification> found = post == null
                ? notificationRepository.findByRecipientAndActorAndTypeAndPostIsNull(recipient, actor, type)
                : notificationRepository.findByRecipientAndActorAndTypeAndPost(recipient, actor, type, post);
        return found.stream().findFirst();
    }

    public List<NotificationDto> getRecent(Long userId) {
        return notificationRepository.findRecent(userId, PageRequest.of(0, RECENT_SIZE)).stream()
                .filter(n -> !n.getActor().isSuspended())   // 정지된 유저의 알림은 가린다
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsRead(userId);
    }

    private NotificationDto toDto(Notification n) {
        return new NotificationDto(
                n.getId(),
                n.getType().name(),
                n.getActor().getUsername(),
                n.getActor().getId(),
                n.getActor().getProfileImageUrl(),
                n.getPost() != null ? n.getPost().getId() : null,
                n.getPost() != null ? n.getPost().getImageUrl() : null,
                n.getComment() != null ? n.getComment().getContent() : null,
                toMessage(n.getType()),
                n.getCreatedAt().toString(),
                n.isRead()
        );
    }

    /**
     * 같은 알림을 다시 만들지 않을 종류인지.
     *
     * 팔로우와 좋아요는 껐다 켤 수 있어서 그대로 두면 알림이 계속 쌓인다.
     * 댓글과 멘션은 같은 사람이 같은 게시글에 몇 번이든 남길 수 있고 매번 알려야 한다.
     *
     * 예전에는 "댓글만 빼고 전부 중복 제거"로 적혀 있었다. 그러면 새 종류가 생길 때마다
     * 아무도 정하지 않았는데 중복 제거 쪽으로 들어가버려서, 실제로 멘션을 추가할 때
     * 두번째 멘션 알림이 사라질 뻔했다. 그래서 어느 쪽인지를 여기에 적어두는 방식으로 바꿨다.
     */
    private boolean dedupes(Notification.Type type) {
        return type == Notification.Type.FOLLOW || type == Notification.Type.POST_LIKE;
    }

    private String toMessage(Notification.Type type) {
        switch (type) {
            case FOLLOW:
                return "회원님을 팔로우하기 시작했습니다.";
            case POST_LIKE:
                return "회원님의 게시글을 좋아합니다.";
            case COMMENT:
                return "회원님의 게시글에 댓글을 남겼습니다.";
            case MENTION:
                return "댓글에서 회원님을 언급했습니다.";
            default:
                return "";
        }
    }
}

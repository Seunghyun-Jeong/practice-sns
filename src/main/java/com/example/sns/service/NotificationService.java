package com.example.sns.service;

import com.example.sns.dto.NotificationDto;
import com.example.sns.entity.Comment;
import com.example.sns.entity.Notification;
import com.example.sns.entity.Post;
import com.example.sns.entity.User;
import com.example.sns.repository.NotificationRepository;
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
    private final UserRepository userRepository;

    /** 드롭다운에 보여줄 최근 알림 개수 */
    private static final int RECENT_SIZE = 20;

    /**
     * 알림을 만든다.
     * 본인이 자기 글에 한 행동은 알림을 만들지 않고,
     * 같은 알림이 이미 있으면 다시 만들지 않는다.
     */
    @Transactional
    public void notify(User recipient, User actor, Notification.Type type, Post post) {
        notify(recipient, actor, type, post, null);
    }

    /** 댓글 알림처럼 어떤 댓글인지까지 함께 남겨야 하는 경우 */
    @Transactional
    public void notify(User recipient, User actor, Notification.Type type, Post post, Comment comment) {
        if (recipient == null || actor == null) {
            return;
        }
        if (recipient.getId().equals(actor.getId())) {
            return;   // 내 행동으로 나에게 알림이 오지 않게
        }

        // 팔로우·좋아요는 껐다 켜는 동작이라 같은 알림이 쌓이면 안 된다.
        // 댓글은 여러 번 달 수 있고 매번 알려야 하므로 중복으로 보지 않는다.
        if (type != Notification.Type.COMMENT
                && findExisting(recipient, actor, type, post).isPresent()) {
            return;
        }

        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setActor(actor);
        notification.setType(type);
        notification.setPost(post);
        notification.setComment(comment);
        notificationRepository.save(notification);
    }

    /** 행동을 취소했을 때 (좋아요 취소, 언팔로우) 알림도 지운다 */
    @Transactional
    public void cancel(User recipient, User actor, Notification.Type type, Post post) {
        if (recipient == null || actor == null || recipient.getId().equals(actor.getId())) {
            return;
        }
        findExisting(recipient, actor, type, post).ifPresent(notificationRepository::delete);
    }

    private Optional<Notification> findExisting(User recipient, User actor, Notification.Type type, Post post) {
        List<Notification> found = post == null
                ? notificationRepository.findByRecipientAndActorAndTypeAndPostIsNull(recipient, actor, type)
                : notificationRepository.findByRecipientAndActorAndTypeAndPost(recipient, actor, type, post);
        return found.stream().findFirst();
    }

    public List<NotificationDto> getRecent(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));

        return notificationRepository.findRecent(user, PageRequest.of(0, RECENT_SIZE)).stream()
                .filter(n -> !n.getActor().isSuspended())   // 정지된 유저의 알림은 가린다
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public long getUnreadCount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));
        return notificationRepository.countByRecipientAndIsReadFalse(user);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));
        notificationRepository.markAllAsRead(user);
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

    private String toMessage(Notification.Type type) {
        switch (type) {
            case FOLLOW:
                return "회원님을 팔로우하기 시작했습니다.";
            case POST_LIKE:
                return "회원님의 게시글을 좋아합니다.";
            case COMMENT:
                return "회원님의 게시글에 댓글을 남겼습니다.";
            default:
                return "";
        }
    }
}

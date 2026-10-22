package com.example.sns.event;

import com.example.sns.entity.Comment;
import com.example.sns.entity.Notification;
import com.example.sns.entity.Post;
import com.example.sns.entity.User;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 좋아요, 댓글, 팔로우가 "알림을 만들거나 지워 달라"고 남기는 기록.
 *
 * 엔티티가 아니라 id만 담는다. 이 이벤트는 원래 트랜잭션이 커밋된 뒤에 처리되는데,
 * 그때는 원래 영속성 컨텍스트가 닫혀 있어서 엔티티의 LAZY 필드를 읽을 수 없다.
 * Outbox 테이블에 저장하고 Kafka로 보낼 때도 이 모양 그대로 직렬화한다.
 *
 * eventId는 Kafka가 같은 메시지를 두 번 이상 줄 수 있어서(at-least-once) 넣었다.
 * 소비자가 이 값으로 이미 처리한 이벤트를 걸러 낸다.
 */
public record NotificationEvent(
        String eventId,
        Action action,
        Notification.Type type,
        Long recipientId,
        Long actorId,
        Long postId,
        Long commentId,
        LocalDateTime occurredAt
) {
    public enum Action { CREATE, CANCEL }

    public static NotificationEvent created(User recipient, User actor, Notification.Type type, Post post) {
        return created(recipient, actor, type, post, null);
    }

    public static NotificationEvent created(User recipient, User actor, Notification.Type type, Post post, Comment comment) {
        return of(Action.CREATE, recipient, actor, type, post, comment);
    }

    public static NotificationEvent cancelled(User recipient, User actor, Notification.Type type, Post post) {
        return of(Action.CANCEL, recipient, actor, type, post, null);
    }

    private static NotificationEvent of(Action action, User recipient, User actor, Notification.Type type, Post post, Comment comment) {
        return new NotificationEvent(
                UUID.randomUUID().toString(),
                action,
                type,
                recipient != null ? recipient.getId() : null,
                actor != null ? actor.getId() : null,
                post != null ? post.getId() : null,
                comment != null ? comment.getId() : null,
                LocalDateTime.now()
        );
    }
}

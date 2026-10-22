package com.example.sns.event;

import com.example.sns.entity.NotificationOutbox;
import com.example.sns.repository.NotificationOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좋아요, 댓글, 팔로우가 발행한 알림 이벤트를 같은 트랜잭션에서 Outbox에 적는다.
 * 알림 반영은 여기서 하지 않고 NotificationOutboxRelay가 나중에 한다.
 *
 * 같은 트랜잭션이어야 하는 이유: 좋아요와 Outbox 줄이 함께 커밋되거나 함께 롤백돼야
 * "좋아요는 남았는데 알림은 영영 안 온다"가 생기지 않는다.
 * @EventListener는 발행한 자리에서 바로, 같은 트랜잭션으로 실행된다.
 *
 * MANDATORY: 트랜잭션 밖에서 발행하면 묶을 트랜잭션이 없다. 그때는 조용히 따로 저장하지 않고
 * 예외로 드러낸다. @TransactionalEventListener(BEFORE_COMMIT)를 쓰지 않은 이유도 같다.
 * 그쪽은 트랜잭션이 없으면 리스너가 불리지 않아 이벤트가 소리 없이 사라진다.
 *
 * Outbox 저장이 실패하면 좋아요도 롤백된다. 같은 DB에 한 줄 넣는 일이라 이게 실패할 상황이면
 * 좋아요 저장도 성하지 않다고 본다.
 */
@Component
@RequiredArgsConstructor
public class NotificationEventListener {
    private final NotificationOutboxRepository outboxRepository;
    private final NotificationEventJson eventJson;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(NotificationEvent event) {
        outboxRepository.save(new NotificationOutbox(
                event.eventId(), event.recipientId(), eventJson.write(event), event.occurredAt()));
    }
}

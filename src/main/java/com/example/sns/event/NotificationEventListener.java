package com.example.sns.event;

import com.example.sns.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 좋아요, 댓글, 팔로우가 커밋된 뒤에 알림을 처리한다.
 *
 * 커밋 뒤에 도는 이유: 알림 저장이나 푸시가 실패해도 좋아요는 남아야 하고,
 * 롤백된 좋아요의 알림이 나가면 안 되기 때문이다.
 * 이 시점에는 원래 트랜잭션이 끝나 있어서 저장하려면 새 트랜잭션(REQUIRES_NEW)이 필요하다.
 * 여기서 난 예외는 Spring이 로그만 남기고 삼키므로 요청은 성공으로 끝난다.
 *
 * 한계: 커밋과 이 리스너 사이에 서버가 죽으면 알림이 사라진다. 다음 단계에서
 * 이벤트를 같은 트랜잭션 안에서 Outbox 테이블에 저장해 이 구멍을 막는다.
 */
@Component
@RequiredArgsConstructor
public class NotificationEventListener {
    private final NotificationService notificationService;

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(NotificationEvent event) {
        notificationService.apply(event);
    }
}

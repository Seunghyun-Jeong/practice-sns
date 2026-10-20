package com.example.sns.support;

import com.example.sns.event.NotificationEvent;
import com.example.sns.service.NotificationService;
import org.springframework.test.context.event.ApplicationEvents;

/**
 * 트랜잭션으로 감싼 테스트에서 알림을 확인할 때 쓴다.
 *
 * 알림은 커밋 뒤에 처리되는데, 테스트 트랜잭션은 끝에 롤백되므로 커밋이 오지 않는다.
 * 그래서 요청이 커밋된 것처럼 기록된 이벤트를 직접 반영한다. 반영한 이벤트는 비워서
 * 다음 요청 때 두 번 반영되지 않게 한다. 커밋 뒤에 도는지 자체는 NotificationEventTest가 본다.
 * 테스트 클래스에 @RecordApplicationEvents가 있어야 이벤트가 기록된다.
 */
public final class RecordedNotifications {

    private RecordedNotifications() {
    }

    public static void apply(ApplicationEvents events, NotificationService notificationService) {
        events.stream(NotificationEvent.class).forEach(notificationService::apply);
        events.clear();
    }
}

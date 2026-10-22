package com.example.sns.event;

import com.example.sns.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka에서 알림 이벤트를 받아 반영한다. 알림을 실제로 만들고 지우는 경로는 이것 하나다.
 *
 * 같은 받는 사람의 이벤트는 같은 파티션에 들어오고, 한 파티션은 소비자 하나가 차례대로 읽으므로 순서가 지켜진다.
 * 서버가 여러 대면 같은 groupId의 소비자끼리 파티션을 나눠 맡는다. 릴레이와 달리 ShedLock이 필요 없다.
 *
 * 오프셋(어디까지 읽었는지)은 Spring이 반영을 마친 뒤에 커밋한다. 반영 도중 서버가 죽으면 그 메시지를 다시 받는다.
 * 그래서 같은 이벤트가 두 번 올 수 있고, eventId로 걸러 내는 일은 다음 단계에서 붙인다.
 * 반영이 실패하면 지금은 Spring 기본 동작대로 몇 번 다시 시도한 뒤 로그만 남기고 넘어간다.
 * 실패한 메시지를 따로 모으는 DLT도 다음 단계에서 붙인다.
 */
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final NotificationService notificationService;
    private final NotificationEventJson eventJson;

    @KafkaListener(topics = NotificationTopic.NAME, groupId = "sns-notification")
    public void consume(String payload) {
        notificationService.apply(eventJson.read(payload));
    }
}

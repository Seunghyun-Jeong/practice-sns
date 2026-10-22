package com.example.sns.event;

import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 알림 Outbox 릴레이와 정리를 주기적으로 돌린다.
 *
 * 서버가 여러 대면 모든 서버에서 @Scheduled가 돈다. @SchedulerLock은 DB의 shedlock 표에
 * "지금 누가 돌고 있다"를 적어서, 그 표를 먼저 잡은 한 대만 실행하고 나머지는 이번 차례를 건너뛰게 한다.
 * 잡은 서버가 죽으면 lockAtMostFor가 지난 뒤 다른 서버가 이어받는다.
 *
 * 한 대만 돌리는 이유: 여러 대가 나눠 처리하면 같은 받는 사람의 좋아요와 취소가 다른 서버로 가서
 * 순서가 뒤집힐 수 있다. 다음 단계에서 릴레이는 "DB에서 꺼내 Kafka에 넣기"만 하므로 한 대로 충분하고,
 * 여러 대로 나눠 처리하는 일은 Kafka 소비자가 받는 사람 id 키로 순서를 지키며 맡는다.
 *
 * 테스트에서는 notification.outbox.relay.enabled=false로 끄고 릴레이를 직접 부른다.
 * 테스트 도중에 스케줄러가 끼어들면 결과가 흔들리기 때문이다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationOutboxScheduler {

    /** 지난 줄을 이 기간 동안 남겨 문의 추적과 지연 측정에 쓴다 */
    private static final Duration RETENTION = Duration.ofDays(7);

    private final NotificationOutboxRelay relay;

    /**
     * fixedDelay: 앞 차례가 끝나고 1초 뒤에 다음 차례를 돈다. 한 서버 안에서 차례가 겹치지 않는다.
     * lockAtMostFor 30초: 한 묶음(200줄)은 이보다 훨씬 빨리 끝난다고 보고 잡았다. 이보다 오래 걸리면
     * 잠금이 풀려 다른 서버가 같은 줄을 집을 수 있으니 BATCH_SIZE를 늘릴 때 같이 본다.
     */
    @Scheduled(fixedDelay = 1000)
    @SchedulerLock(name = "notificationOutboxRelay", lockAtMostFor = "PT30S")
    public void relayPending() {
        relay.relayPending();
    }

    @Scheduled(cron = "0 30 4 * * *")
    @SchedulerLock(name = "notificationOutboxCleanup", lockAtMostFor = "PT10M")
    public void cleanUp() {
        relay.deleteProcessedBefore(LocalDateTime.now().minus(RETENTION));
    }
}

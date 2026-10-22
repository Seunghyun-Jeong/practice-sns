package com.example.sns.event;

import com.example.sns.entity.NotificationOutbox;
import com.example.sns.repository.NotificationOutboxRepository;
import com.example.sns.service.NotificationService;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox에 쌓인 알림 이벤트를 id 순으로 꺼내 반영한다.
 * 지금은 NotificationService.apply를 바로 부르고, 다음 단계에서 Kafka로 보내는 것으로 바뀐다.
 *
 * 줄마다 트랜잭션을 따로 연다. 반영과 "처리됨" 표시가 함께 커밋돼야 하고,
 * 한 줄이 실패했다고 앞서 성공한 줄까지 롤백되면 안 되기 때문이다.
 *
 * 실패하면 그 받는 사람의 뒤 줄은 이번 차례에 건너뛴다. 좋아요 반영이 실패했는데 뒤의
 * 좋아요 취소를 먼저 반영하면, 다음 차례에 좋아요가 반영되면서 취소된 알림이 남는다.
 * 다른 사람의 줄은 계속 처리해서 한 사람의 실패가 전체 알림을 막지 않게 한다.
 * MAX_ATTEMPTS번 실패한 줄은 실패로 확정해 더 시도하지 않는다(그래야 그 사람의 뒤 줄이 풀린다).
 *
 * 이 클래스는 서버 여러 대에서 동시에 돌면 같은 줄을 두 번 반영한다.
 * 그래서 주기 실행은 NotificationOutboxScheduler가 ShedLock으로 한 대에서만 돌린다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationOutboxRelay {

    /**
     * 한 번에 꺼낼 줄 수. 1초마다 돌면 초당 이만큼까지 처리한다.
     * 수만 MAU에서 알림 이벤트는 많아야 초당 수십 건으로 추정해 넉넉히 잡았다.
     */
    static final int BATCH_SIZE = 200;

    /** 일시적인 장애(DB 순간 끊김 등)는 몇 번 안에 풀린다고 보고, 그 뒤로도 실패하면 데이터 문제로 본다 */
    static final int MAX_ATTEMPTS = 5;

    /** 지운 줄 수를 잠금 하나에 묶지 않으려고 끊어서 지운다 */
    private static final int DELETE_CHUNK_SIZE = 1000;

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationService notificationService;
    private final NotificationEventJson eventJson;
    private final TransactionTemplate transactionTemplate;

    /** 처리 안 된 줄을 한 묶음 반영하고, 반영한 줄 수를 돌려준다 */
    public int relayPending() {
        List<NotificationOutbox> pending = outboxRepository.findPending(Limit.of(BATCH_SIZE));
        Set<Long> deferredRecipients = new HashSet<>();
        int processed = 0;

        for (NotificationOutbox row : pending) {
            if (deferredRecipients.contains(row.getRecipientId())) {
                continue;
            }
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    notificationService.apply(eventJson.read(row.getPayload()));
                    outboxRepository.markProcessed(row.getId(), LocalDateTime.now());
                });
                processed++;
            } catch (RuntimeException e) {
                deferredRecipients.add(row.getRecipientId());
                recordFailure(row.getId(), e);
            }
        }
        return processed;
    }

    private void recordFailure(Long rowId, RuntimeException cause) {
        transactionTemplate.executeWithoutResult(status ->
                outboxRepository.findById(rowId).ifPresent(row -> {
                    boolean gaveUp = row.recordFailure(cause.toString(), MAX_ATTEMPTS, LocalDateTime.now());
                    if (gaveUp) {
                        log.warn("알림 Outbox {}번을 {}번 실패해 포기한다: {}", rowId, MAX_ATTEMPTS, cause.toString());
                    }
                }));
    }

    /** cutoff 전에 처리된 줄을 지우고, 지운 줄 수를 돌려준다 */
    public int deleteProcessedBefore(LocalDateTime cutoff) {
        int deleted = 0;
        List<Long> ids;
        do {
            ids = outboxRepository.findProcessedIdsBefore(cutoff, Limit.of(DELETE_CHUNK_SIZE));
            if (!ids.isEmpty()) {
                outboxRepository.deleteAllByIdInBatch(ids);
                deleted += ids.size();
            }
        } while (ids.size() == DELETE_CHUNK_SIZE);
        return deleted;
    }
}

package com.example.sns.event;

import com.example.sns.entity.NotificationOutbox;
import com.example.sns.repository.NotificationOutboxRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.RetriableException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox에 쌓인 알림 이벤트를 id 순으로 꺼내 Kafka로 보낸다. 반영은 NotificationEventConsumer가 한다.
 * "처리됨"은 Kafka가 받았다고 답했다는 뜻이다(acks=all이라 복제본까지 적힌 뒤).
 *
 * 한 묶음을 먼저 모두 보내 놓고 응답을 한꺼번에 기다린다. 줄마다 응답을 기다리면 묶음 하나에
 * 왕복 시간이 200번 쌓이고, 프로듀서가 여러 줄을 한 요청으로 묶어 보낼 기회도 없어진다.
 *
 * 실패하면 그 받는 사람의 뒤 줄은 이미 Kafka에 들어갔더라도 "처리됨"으로 적지 않고 다음 차례에 다시 보낸다.
 * 좋아요(A)가 실패하고 취소(B)만 들어갔다면, 다음 차례에 A, B를 다시 보내 파티션에는 B, A, B가 남는다.
 * 소비자가 끝까지 읽으면 마지막 B가 A를 지우므로 결과가 맞다. 대신 B는 두 번 반영되며,
 * 같은 이벤트를 두 번 받는 일은 서버가 죽을 때도 생기므로 다음 단계에서 eventId로 걸러 낸다.
 * 다른 사람의 줄은 계속 처리해서 한 사람의 실패가 전체 알림을 막지 않게 한다.
 *
 * 시도 횟수는 그 줄 자체가 문제인 실패(메시지가 너무 큼 등)만 센다. 브로커가 꺼졌거나 응답이 늦은 실패
 * (RetriableException)는 줄 탓이 아니어서 세지 않는다. 셌다면 Kafka가 5초만 멈춰도 쌓인 줄이 모두 실패로 확정된다.
 * MAX_ATTEMPTS번 실패한 줄은 실패로 확정해 더 시도하지 않는다(그래야 그 사람의 뒤 줄이 풀린다).
 *
 * 이 클래스는 서버 여러 대에서 동시에 돌면 같은 줄을 두 번 보내고 순서도 섞인다.
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

    /** 줄 자체의 문제로 이만큼 실패하면 다시 보내도 같다고 보고 포기한다 */
    static final int MAX_ATTEMPTS = 5;

    /** 지운 줄 수를 잠금 하나에 묶지 않으려고 끊어서 지운다 */
    private static final int DELETE_CHUNK_SIZE = 1000;

    private final NotificationOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;

    /** 처리 안 된 줄을 한 묶음 Kafka로 보내고, Kafka가 받은 줄 수를 돌려준다 */
    public int relayPending() {
        List<NotificationOutbox> pending = outboxRepository.findPending(Limit.of(BATCH_SIZE));
        List<Sent> sent = send(pending);

        Set<Long> deferredRecipients = new HashSet<>();
        List<Long> acknowledged = new ArrayList<>();
        for (Sent s : sent) {
            Long recipientId = s.row().getRecipientId();
            Throwable failure = awaitFailure(s.result());
            if (failure != null) {
                deferredRecipients.add(recipientId);
                recordFailure(s.row().getId(), failure);
            } else if (!deferredRecipients.contains(recipientId)) {
                acknowledged.add(s.row().getId());
            }
        }

        if (!acknowledged.isEmpty()) {
            transactionTemplate.executeWithoutResult(status ->
                    outboxRepository.markProcessed(acknowledged, LocalDateTime.now()));
        }
        return acknowledged.size();
    }

    /**
     * 브로커에 닿지 못하면 send가 토픽 정보를 max.block.ms(5초)만큼 기다리다 KafkaException을 던진다
     * (실제 브로커를 끄고 확인). 그때 남은 줄을 계속 보내면 줄마다 5초씩 막혀 ShedLock 잠금 시간(30초)을 넘기므로
     * 이번 차례는 거기서 멈춘다. 예외를 실패한 결과로 바꿔 두어야 앞서 보낸 줄의 응답도 마저 확인한다.
     * 멈춘 뒤의 줄은 보내지 않았으니 다음 차례에 순서대로 나간다.
     */
    private List<Sent> send(List<NotificationOutbox> pending) {
        List<Sent> sent = new ArrayList<>();
        for (NotificationOutbox row : pending) {
            String key = row.getRecipientId() != null ? row.getRecipientId().toString() : null;
            CompletableFuture<SendResult<String, String>> result;
            try {
                result = kafkaTemplate.send(NotificationTopic.NAME, key, row.getPayload());
            } catch (RuntimeException e) {
                result = CompletableFuture.failedFuture(e);
            }
            sent.add(new Sent(row, result));
            if (result.isCompletedExceptionally()) {
                break;
            }
        }
        return sent;
    }

    /** 응답은 delivery.timeout.ms(10초) 안에 성공이든 실패든 반드시 온다 */
    private Throwable awaitFailure(CompletableFuture<SendResult<String, String>> result) {
        try {
            result.get();
            return null;
        } catch (ExecutionException e) {
            return e.getCause();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return e;
        }
    }

    private record Sent(NotificationOutbox row, CompletableFuture<SendResult<String, String>> result) {
    }

    private void recordFailure(Long rowId, Throwable cause) {
        if (isTransient(cause)) {
            // KafkaException("Send failed")만 찍으면 브로커가 꺼졌는지 토픽이 없는지 구분이 안 돼서 가장 안쪽 원인을 남긴다
            log.warn("알림 Outbox {}번을 Kafka에 보내지 못했다. 다음 차례에 다시 보낸다: {}",
                    rowId, NestedExceptionUtils.getMostSpecificCause(cause).toString());
            return;
        }
        transactionTemplate.executeWithoutResult(status ->
                outboxRepository.findById(rowId).ifPresent(row -> {
                    boolean gaveUp = row.recordFailure(cause.toString(), MAX_ATTEMPTS, LocalDateTime.now());
                    if (gaveUp) {
                        log.warn("알림 Outbox {}번을 {}번 실패해 포기한다: {}", rowId, MAX_ATTEMPTS, cause.toString());
                    }
                }));
    }

    /** KafkaTemplate이 프로듀서의 예외를 KafkaProducerException으로 한 번 감싸므로 원인까지 내려가 본다 */
    private boolean isTransient(Throwable cause) {
        for (Throwable t = cause; t != null; t = t.getCause()) {
            if (t instanceof RetriableException || t instanceof InterruptedException) {
                return true;
            }
        }
        return false;
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

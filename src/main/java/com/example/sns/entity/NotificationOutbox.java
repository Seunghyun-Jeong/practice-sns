package com.example.sns.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 아직 반영하지 않은 알림 이벤트를 적어 두는 표(Outbox).
 *
 * 좋아요와 같은 트랜잭션에서 한 줄을 넣는다. 그래서 좋아요가 커밋되면 이 줄도 반드시 남고,
 * 롤백되면 이 줄도 없다. 반영은 릴레이가 나중에 따로 한다. 커밋 직후 서버가 죽어도
 * 줄이 남아 있으니 다시 살아난 뒤(또는 다른 서버가) 이어서 반영한다.
 *
 * 처리한 줄을 바로 지우지 않고 processedAt을 남기는 이유: "알림이 안 왔다"는 문의에서
 * 이벤트가 안 생긴 건지, 릴레이가 막힌 건지, 반영 뒤의 문제인지를 나눠 볼 수 있고,
 * processedAt - createdAt이 곧 알림 지연이라 잴 수 있다. 오래된 줄은 릴레이가 끊어서 지운다.
 *
 * id 순서가 곧 발생 순서다. 릴레이가 id 순으로 반영해야 좋아요 뒤의 취소가 먼저 반영되는 일이 없다.
 */
@Entity
@Getter
@NoArgsConstructor
@Table(indexes = {
        // 릴레이가 매초 "처리도 실패도 안 된 줄을 id 순으로" 읽는다. 지난 줄이 쌓여도 이 범위만 읽는다
        @Index(name = "idx_outbox_pending", columnList = "processedAt, failedAt, id")
})
public class NotificationOutbox {

    /** 실패한 오류 메시지는 원인 파악에 앞부분이면 충분하다 */
    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String eventId;

    /** 실패한 받는 사람의 뒤 줄만 미루려고 본문과 따로 둔다 */
    private Long recipientId;

    /** NotificationEvent를 JSON으로 적은 것. 다음 단계에서 Kafka로 보낼 때도 이 모양 그대로다 */
    @Column(nullable = false, length = 2000)
    private String payload;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime processedAt;

    private int attempts;

    /** 재시도 상한을 넘겨 더 이상 시도하지 않는 줄. 지우지 않고 남겨 원인을 본다 */
    private LocalDateTime failedAt;

    @Column(length = MAX_ERROR_LENGTH)
    private String lastError;

    public NotificationOutbox(String eventId, Long recipientId, String payload, LocalDateTime createdAt) {
        this.eventId = eventId;
        this.recipientId = recipientId;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    /** 실패를 한 번 센다. 상한에 닿으면 실패로 확정하고 true를 돌려준다 */
    public boolean recordFailure(String error, int maxAttempts, LocalDateTime now) {
        attempts++;
        lastError = error != null && error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error;
        if (attempts >= maxAttempts) {
            failedAt = now;
            return true;
        }
        return false;
    }
}

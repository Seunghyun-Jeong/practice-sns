package com.example.sns.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 유저 신고.
 * reporter 가 targetUser 를 신고한 기록이다.
 *
 * 인덱스 두 개의 용도가 다르다.
 * - (target_user_id, status): 관리자 목록이 피신고자별로 대기 중인 신고를 집계할 때
 * - (reporter_id, created_at): 신고자의 최근 신고 이력을 셀 때
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "report", indexes = {
        @Index(name = "idx_report_target_status", columnList = "target_user_id, status"),
        @Index(name = "idx_report_reporter_created", columnList = "reporter_id, created_at")
})
public class Report {

    /** 신고 사유. 화면의 드롭다운 항목과 1:1로 대응한다. */
    public enum Reason {
        SPAM("스팸 또는 도배"),
        ABUSE("욕설 또는 괴롭힘"),
        SEXUAL("음란물"),
        IMPERSONATION("사칭"),
        ILLEGAL("불법 정보"),
        OTHER("기타");

        private final String label;

        Reason(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /**
     * 처리 상태.
     * REJECTED 가 있어야 신고자의 반려 비율을 낼 수 있고,
     * 그 비율이 곧 악성 신고자를 가려내는 근거가 된다.
     */
    public enum Status {
        PENDING,   // 아직 관리자가 보지 않음
        RESOLVED,  // 신고가 받아들여져 제재함
        REJECTED   // 신고가 근거 없다고 판단해 반려함
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 신고한 사람 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    /** 신고당한 사람 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id", nullable = false)
    private User targetUser;

    /**
     * MySQL ENUM이 아니라 문자열 컬럼으로 둔다.
     * ddl-auto=update 는 이미 있는 컬럼의 정의를 바꾸지 않아서,
     * ENUM으로 굳으면 사유나 상태를 새로 추가할 때 저장이 잘린다.
     * 알림 종류에 MENTION을 추가하다 실제로 겪은 문제다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private Reason reason;

    /** 신고자가 직접 적은 상세 내용 */
    @Column(length = 500)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private Status status = Status.PENDING;

    private LocalDateTime createdAt = LocalDateTime.now();

    /** 관리자가 처리한 시각 (대기 중이면 null) */
    private LocalDateTime handledAt;

    public void resolve() {
        this.status = Status.RESOLVED;
        this.handledAt = LocalDateTime.now();
    }

    public void reject() {
        this.status = Status.REJECTED;
        this.handledAt = LocalDateTime.now();
    }
}

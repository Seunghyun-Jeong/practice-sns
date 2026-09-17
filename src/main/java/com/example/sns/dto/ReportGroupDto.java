package com.example.sns.dto;

import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 관리자 신고 목록의 한 줄.
 * 신고 한 건이 아니라 <b>피신고자 한 명</b>이 한 줄이고,
 * 그 사람이 받은 신고 수가 reportCount 다.
 *
 * JPQL 생성자 표현식으로 직접 채운다. 그룹마다 유저를 다시 조회하면
 * 피드에서 없앤 N+1과 같은 모양이 되기 때문이다.
 */
@Getter
public class ReportGroupDto {

    private final Long targetUserId;
    private final String username;
    private final String profileImageUrl;
    private final LocalDateTime suspendedUntil;
    private final Long reportCount;

    /** 대기 탭에서는 마지막 신고 시각, 처리 완료 탭에서는 마지막 처리 시각 */
    private final LocalDateTime lastActivityAt;

    private final Long resolvedCount;
    private final Long rejectedCount;

    /** 대기 목록용. 아직 처리된 것이 없으므로 제재와 반려 수는 0이다. */
    public ReportGroupDto(Long targetUserId, String username, String profileImageUrl,
                          LocalDateTime suspendedUntil, Long reportCount, LocalDateTime lastActivityAt) {
        this(targetUserId, username, profileImageUrl, suspendedUntil, reportCount, lastActivityAt, 0L, 0L);
    }

    /** 처리 완료 목록용. 한 사람에게 제재와 반려가 섞여 있을 수 있어 나눠서 받는다. */
    public ReportGroupDto(Long targetUserId, String username, String profileImageUrl,
                          LocalDateTime suspendedUntil, Long reportCount, LocalDateTime lastActivityAt,
                          Long resolvedCount, Long rejectedCount) {
        this.targetUserId = targetUserId;
        this.username = username;
        this.profileImageUrl = profileImageUrl;
        this.suspendedUntil = suspendedUntil;
        this.reportCount = reportCount;
        this.lastActivityAt = lastActivityAt;
        this.resolvedCount = resolvedCount != null ? resolvedCount : 0L;
        this.rejectedCount = rejectedCount != null ? rejectedCount : 0L;
    }

    public boolean isSuspended() {
        return suspendedUntil != null && suspendedUntil.isAfter(LocalDateTime.now());
    }
}

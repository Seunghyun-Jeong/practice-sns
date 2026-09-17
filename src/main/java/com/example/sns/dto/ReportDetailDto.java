package com.example.sns.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 관리자 신고 상세 화면의 신고 한 건 */
@Getter
@AllArgsConstructor
public class ReportDetailDto {
    private Long id;
    private Long reporterId;
    private String reporterUsername;
    private String reasonLabel;
    private String detail;
    private String status;
    private LocalDateTime createdAt;

    /** 이 신고자가 최근 30일간 넣은 신고 수 */
    private long reporterRecentCount;
    /** 그중 반려된 수 */
    private long reporterRejectedCount;

    public boolean isPending() {
        return "PENDING".equals(status);
    }
}

package com.example.sns.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 신고자 한 명의 최근 신고 이력 집계.
 *
 * 신고 건수만으로는 악성 신고자를 가려낼 수 없다.
 * 나쁜 유저를 잘 찾아내는 사람도 건수가 높기 때문이다.
 * 반려된 비율이 함께 있어야 판단이 된다.
 */
@Getter
@AllArgsConstructor
public class ReporterStatsDto {
    private Long reporterId;
    private Long totalCount;
    private Long rejectedCount;
}

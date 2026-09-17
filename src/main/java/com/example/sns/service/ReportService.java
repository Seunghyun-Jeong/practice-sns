package com.example.sns.service;

import com.example.sns.dto.ReportDetailDto;
import com.example.sns.dto.ReportGroupDto;
import com.example.sns.dto.ReporterStatsDto;
import com.example.sns.entity.Report;
import com.example.sns.entity.User;
import com.example.sns.repository.ReportRepository;
import com.example.sns.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    /**
     * 신고자의 최근 이력을 몇 일까지 볼지.
     * 7일로 잡으면 표본이 한두 건이라 반려 비율이 크게 튄다.
     */
    private static final int REPORTER_HISTORY_DAYS = 30;

    /** 상세 내용 길이 상한 (엔티티 컬럼 길이와 맞춘다) */
    private static final int DETAIL_MAX_LENGTH = 500;

    @Transactional
    public void report(Long reporterId, Long targetUserId, String reasonName, String detail) {
        if (reporterId == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        if (reporterId.equals(targetUserId)) {
            throw new IllegalArgumentException("자기 자신은 신고할 수 없습니다.");
        }

        Report.Reason reason = parseReason(reasonName);

        String trimmed = detail == null ? "" : detail.trim();
        if (trimmed.length() > DETAIL_MAX_LENGTH) {
            throw new IllegalArgumentException("상세 내용은 " + DETAIL_MAX_LENGTH + "자까지 쓸 수 있습니다.");
        }

        if (reportRepository.existsByReporterIdAndTargetUserIdAndStatus(
                reporterId, targetUserId, Report.Status.PENDING)) {
            throw new IllegalArgumentException("이미 신고한 유저입니다. 처리될 때까지 기다려주세요.");
        }

        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("신고할 유저를 찾을 수 없습니다."));

        Report report = new Report();
        report.setReporter(reporter);
        report.setTargetUser(target);
        report.setReason(reason);
        report.setDetail(trimmed.isEmpty() ? null : trimmed);
        reportRepository.save(report);
    }

    private Report.Reason parseReason(String reasonName) {
        if (reasonName == null || reasonName.isBlank()) {
            throw new IllegalArgumentException("신고 사유를 선택해주세요.");
        }
        try {
            return Report.Reason.valueOf(reasonName);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("신고 사유를 선택해주세요.");
        }
    }

    /** 관리자 목록: 대기 중인 신고를 피신고자별로 묶어 한 줄씩 */
    @Transactional(readOnly = true)
    public List<ReportGroupDto> getPendingGroups() {
        return reportRepository.findGroupsByStatus(Report.Status.PENDING);
    }

    /** 관리자 목록: 처리가 끝난 신고를 피신고자별로 묶어 한 줄씩 */
    @Transactional(readOnly = true)
    public List<ReportGroupDto> getHandledGroups() {
        return reportRepository.findHandledGroups(
                Report.Status.PENDING, Report.Status.RESOLVED, Report.Status.REJECTED);
    }

    /**
     * 관리자 상세: 한 유저가 받은 신고 전부.
     * 신고자별 최근 이력은 배치 쿼리 한 번으로 모아 붙인다.
     */
    @Transactional(readOnly = true)
    public List<ReportDetailDto> getReportsAgainst(Long targetUserId) {
        List<Report> reports = reportRepository.findByTargetWithReporter(targetUserId);
        if (reports.isEmpty()) {
            return List.of();
        }

        List<Long> reporterIds = reports.stream()
                .map(r -> r.getReporter().getId())
                .distinct()
                .toList();

        LocalDateTime since = LocalDateTime.now().minusDays(REPORTER_HISTORY_DAYS);
        Map<Long, ReporterStatsDto> statsById = reportRepository
                .findReporterStats(reporterIds, since, Report.Status.REJECTED).stream()
                .collect(Collectors.toMap(ReporterStatsDto::getReporterId, Function.identity()));

        return reports.stream().map(r -> {
            // 최근 30일 안에 신고가 없으면 집계에 안 잡히므로 0으로 둔다
            ReporterStatsDto stats = statsById.get(r.getReporter().getId());
            long total = stats != null ? stats.getTotalCount() : 0L;
            long rejected = stats != null && stats.getRejectedCount() != null ? stats.getRejectedCount() : 0L;

            return new ReportDetailDto(
                    r.getId(),
                    r.getReporter().getId(),
                    r.getReporter().getUsername(),
                    r.getReason().getLabel(),
                    r.getDetail(),
                    r.getStatus().name(),
                    r.getCreatedAt(),
                    total,
                    rejected
            );
        }).toList();
    }

    /** 신고를 받아들여 해당 유저를 정지시키고, 그 유저에 대한 대기 신고를 모두 처리 완료로 바꾼다 */
    @Transactional
    public void resolveAll(Long targetUserId, String duration) {
        userService.suspendUser(targetUserId, duration);
        reportRepository.findByTargetUserIdAndStatus(targetUserId, Report.Status.PENDING)
                .forEach(Report::resolve);
    }

    /** 근거 없다고 판단해 해당 유저에 대한 대기 신고를 모두 반려한다 */
    @Transactional
    public void rejectAll(Long targetUserId) {
        List<Report> pending = reportRepository.findByTargetUserIdAndStatus(targetUserId, Report.Status.PENDING);
        if (pending.isEmpty()) {
            throw new IllegalArgumentException("처리할 신고가 없습니다.");
        }
        pending.forEach(Report::reject);
    }

    /**
     * 신고 한 건만 반려한다.
     * 그룹 단위로만 처리하면, 정당한 신고 아홉 건에 악성 신고 한 건이 섞였을 때
     * 악성 신고까지 "받아들여진 신고"로 집계되어 반려 비율이 흐려진다.
     */
    @Transactional
    public void rejectOne(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("신고를 찾을 수 없습니다."));
        if (report.getStatus() != Report.Status.PENDING) {
            throw new IllegalArgumentException("이미 처리된 신고입니다.");
        }
        report.reject();
    }

    public int getReporterHistoryDays() {
        return REPORTER_HISTORY_DAYS;
    }
}

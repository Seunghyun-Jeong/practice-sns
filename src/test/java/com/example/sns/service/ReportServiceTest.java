package com.example.sns.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.sns.dto.ReportDetailDto;
import com.example.sns.dto.ReporterStatsDto;
import com.example.sns.entity.Report;
import com.example.sns.entity.User;
import com.example.sns.repository.ReportRepository;
import com.example.sns.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private ReportService reportService;

    private User reporter;
    private User target;

    @BeforeEach
    void setUp() {
        reporter = new User();
        reporter.setId(1L);
        reporter.setUsername("reporter");

        target = new User();
        target.setId(2L);
        target.setUsername("target");
    }

    private Report report(Long id, User from, Report.Status status) {
        Report r = new Report();
        r.setId(id);
        r.setReporter(from);
        r.setTargetUser(target);
        r.setReason(Report.Reason.SPAM);
        r.setStatus(status);
        r.setCreatedAt(LocalDateTime.now());
        return r;
    }

    @Test
    @DisplayName("신고하면 대기 상태로 저장된다")
    void 신고한다() {
        when(reportRepository.existsByReporterIdAndTargetUserIdAndStatus(1L, 2L, Report.Status.PENDING))
                .thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        reportService.report(1L, 2L, "SPAM", "  도배를 합니다  ");

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());

        Report saved = captor.getValue();
        assertThat(saved.getReason()).isEqualTo(Report.Reason.SPAM);
        assertThat(saved.getStatus()).isEqualTo(Report.Status.PENDING);
        assertThat(saved.getDetail()).isEqualTo("도배를 합니다");
    }

    @Test
    @DisplayName("상세 내용을 비워도 신고할 수 있다")
    void 상세내용_없이_신고한다() {
        when(reportRepository.existsByReporterIdAndTargetUserIdAndStatus(1L, 2L, Report.Status.PENDING))
                .thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        reportService.report(1L, 2L, "ABUSE", "   ");

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        assertThat(captor.getValue().getDetail()).isNull();
    }

    @Test
    @DisplayName("자기 자신은 신고할 수 없다")
    void 자기자신_신고는_거부() {
        assertThatThrownBy(() -> reportService.report(1L, 1L, "SPAM", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("자기 자신");

        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("없는 사유를 보내면 거부한다")
    void 잘못된_사유는_거부() {
        assertThatThrownBy(() -> reportService.report(1L, 2L, "NOT_A_REASON", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사유");

        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("처리되지 않은 신고가 남아 있으면 같은 대상을 다시 신고할 수 없다")
    void 중복_신고는_거부() {
        when(reportRepository.existsByReporterIdAndTargetUserIdAndStatus(1L, 2L, Report.Status.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> reportService.report(1L, 2L, "SPAM", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 신고한");

        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("상세 내용이 500자를 넘으면 거부한다")
    void 상세내용_길이_제한() {
        String tooLong = "가".repeat(501);

        assertThatThrownBy(() -> reportService.report(1L, 2L, "SPAM", tooLong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("500");

        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("상세 화면은 신고자의 최근 신고 수와 반려 수를 함께 준다")
    void 신고자_이력을_붙인다() {
        Report r = report(10L, reporter, Report.Status.PENDING);
        when(reportRepository.findByTargetWithReporter(2L)).thenReturn(List.of(r));
        when(reportRepository.findReporterStats(anyList(), any(LocalDateTime.class), any()))
                .thenReturn(List.of(new ReporterStatsDto(1L, 12L, 9L)));

        List<ReportDetailDto> result = reportService.getReportsAgainst(2L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getReporterUsername()).isEqualTo("reporter");
        assertThat(result.get(0).getReporterRecentCount()).isEqualTo(12L);
        assertThat(result.get(0).getReporterRejectedCount()).isEqualTo(9L);
        assertThat(result.get(0).isPending()).isTrue();
    }

    @Test
    @DisplayName("최근 이력이 없는 신고자는 0으로 표시된다")
    void 이력이_없으면_0() {
        Report r = report(10L, reporter, Report.Status.PENDING);
        when(reportRepository.findByTargetWithReporter(2L)).thenReturn(List.of(r));
        when(reportRepository.findReporterStats(anyList(), any(LocalDateTime.class), any()))
                .thenReturn(List.of());

        List<ReportDetailDto> result = reportService.getReportsAgainst(2L);

        assertThat(result.get(0).getReporterRecentCount()).isZero();
        assertThat(result.get(0).getReporterRejectedCount()).isZero();
    }

    @Test
    @DisplayName("정지 처리하면 대기 중인 신고가 모두 처리 완료가 된다")
    void 정지하면_대기신고를_모두_처리() {
        Report a = report(10L, reporter, Report.Status.PENDING);
        Report b = report(11L, reporter, Report.Status.PENDING);
        when(reportRepository.findByTargetUserIdAndStatus(2L, Report.Status.PENDING))
                .thenReturn(List.of(a, b));

        reportService.resolveAll(2L, "7d");

        verify(userService).suspendUser(2L, "7d");
        assertThat(a.getStatus()).isEqualTo(Report.Status.RESOLVED);
        assertThat(b.getStatus()).isEqualTo(Report.Status.RESOLVED);
        assertThat(a.getHandledAt()).isNotNull();
    }

    @Test
    @DisplayName("전체 반려하면 대기 중인 신고가 모두 반려된다")
    void 전체_반려() {
        Report a = report(10L, reporter, Report.Status.PENDING);
        when(reportRepository.findByTargetUserIdAndStatus(2L, Report.Status.PENDING))
                .thenReturn(List.of(a));

        reportService.rejectAll(2L);

        assertThat(a.getStatus()).isEqualTo(Report.Status.REJECTED);
    }

    @Test
    @DisplayName("반려할 대기 신고가 없으면 거부한다")
    void 반려할_신고가_없으면_거부() {
        when(reportRepository.findByTargetUserIdAndStatus(2L, Report.Status.PENDING))
                .thenReturn(List.of());

        assertThatThrownBy(() -> reportService.rejectAll(2L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("신고 한 건만 반려할 수 있다")
    void 개별_반려() {
        Report a = report(10L, reporter, Report.Status.PENDING);
        when(reportRepository.findById(10L)).thenReturn(Optional.of(a));

        reportService.rejectOne(10L);

        assertThat(a.getStatus()).isEqualTo(Report.Status.REJECTED);
    }

    @Test
    @DisplayName("이미 처리된 신고는 다시 반려할 수 없다")
    void 처리된_신고는_재반려_불가() {
        Report a = report(10L, reporter, Report.Status.RESOLVED);
        when(reportRepository.findById(10L)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> reportService.rejectOne(10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 처리된");
    }
}

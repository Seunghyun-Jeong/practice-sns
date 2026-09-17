package com.example.sns.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sns.dto.ReportDetailDto;
import com.example.sns.dto.ReportGroupDto;
import com.example.sns.entity.Report;
import com.example.sns.entity.User;
import com.example.sns.repository.ReportRepository;
import com.example.sns.repository.UserRepository;
import com.example.sns.service.ReportService;
import com.example.sns.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 유저 신고 API 테스트.
 * 그룹 집계 쿼리는 실제 DB에서만 검증되므로 여기서 함께 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReportApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private ReportService reportService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    private User reporter;
    private User target;
    private User admin;
    private Cookie reporterCookie;
    private Cookie adminCookie;

    @BeforeEach
    void setUp() {
        reporter = createUser("reporter1", User.Role.USER);
        target = createUser("target1", User.Role.USER);
        admin = createUser("admin1", User.Role.ADMIN);
        reporterCookie = createCookie(reporter);
        adminCookie = createCookie(admin);
    }

    private User createUser(String username, User.Role role) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("Test1234!"));
        user.setRole(role);
        return userRepository.save(user);
    }

    private Cookie createCookie(User user) {
        return new Cookie("JWT_TOKEN",
                jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole().name()));
    }

    private String body(String reason, String detail) {
        return "{\"reason\":\"" + reason + "\",\"detail\":\"" + detail + "\"}";
    }

    @Test
    @DisplayName("로그인하지 않으면 신고할 수 없다")
    void 비로그인_신고는_401() throws Exception {
        mockMvc.perform(post("/api/users/{id}/report", target.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("SPAM", "도배")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("사유와 상세 내용을 보내면 신고가 접수된다")
    void 신고_접수() throws Exception {
        mockMvc.perform(post("/api/users/{id}/report", target.getId())
                        .cookie(reporterCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ABUSE", "욕설을 했습니다")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        assertThat(reportRepository.countByStatus(Report.Status.PENDING)).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 대상을 연속으로 신고하면 400을 준다")
    void 중복_신고는_400() throws Exception {
        mockMvc.perform(post("/api/users/{id}/report", target.getId())
                        .cookie(reporterCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("SPAM", "도배")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/users/{id}/report", target.getId())
                        .cookie(reporterCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("SPAM", "또 도배")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("자기 자신을 신고하면 400을 준다")
    void 자기자신_신고는_400() throws Exception {
        mockMvc.perform(post("/api/users/{id}/report", reporter.getId())
                        .cookie(reporterCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("SPAM", "테스트")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("일반 유저는 신고를 처리할 수 없다")
    void 일반유저의_신고처리는_403() throws Exception {
        mockMvc.perform(patch("/api/reports/users/{id}/reject", target.getId())
                        .cookie(reporterCookie))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("여러 명이 한 명을 신고하면 목록에 줄이 늘지 않고 건수만 올라간다")
    void 피신고자별로_묶인다() {
        User second = createUser("reporter2", User.Role.USER);
        User third = createUser("reporter3", User.Role.USER);

        reportService.report(reporter.getId(), target.getId(), "SPAM", "도배");
        reportService.report(second.getId(), target.getId(), "ABUSE", "욕설");
        reportService.report(third.getId(), target.getId(), "SEXUAL", null);

        List<ReportGroupDto> groups = reportService.getPendingGroups();

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).getTargetUserId()).isEqualTo(target.getId());
        assertThat(groups.get(0).getUsername()).isEqualTo("target1");
        assertThat(groups.get(0).getReportCount()).isEqualTo(3L);
        assertThat(groups.get(0).getLastActivityAt()).isNotNull();
    }

    @Test
    @DisplayName("신고를 많이 받은 유저가 목록 위로 온다")
    void 건수가_많은_순서로_정렬된다() {
        User other = createUser("target2", User.Role.USER);
        User second = createUser("reporter2", User.Role.USER);

        reportService.report(reporter.getId(), other.getId(), "SPAM", null);
        reportService.report(reporter.getId(), target.getId(), "SPAM", null);
        reportService.report(second.getId(), target.getId(), "SPAM", null);

        List<ReportGroupDto> groups = reportService.getPendingGroups();

        assertThat(groups).hasSize(2);
        assertThat(groups.get(0).getTargetUserId()).isEqualTo(target.getId());
        assertThat(groups.get(0).getReportCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("처리한 신고는 대기 목록에서 빠지고 처리 완료 목록으로 넘어간다")
    void 처리하면_완료_목록으로() throws Exception {
        reportService.report(reporter.getId(), target.getId(), "SPAM", "도배");

        mockMvc.perform(patch("/api/reports/users/{id}/resolve", target.getId())
                        .param("duration", "7d")
                        .cookie(adminCookie))
                .andExpect(status().isOk());

        assertThat(reportService.getPendingGroups()).isEmpty();

        List<ReportGroupDto> handled = reportService.getHandledGroups();
        assertThat(handled).hasSize(1);
        assertThat(handled.get(0).getTargetUserId()).isEqualTo(target.getId());
        assertThat(handled.get(0).getReportCount()).isEqualTo(1L);
        assertThat(handled.get(0).getResolvedCount()).isEqualTo(1L);
        assertThat(handled.get(0).getRejectedCount()).isZero();
        assertThat(handled.get(0).getLastActivityAt()).isNotNull();
    }

    @Test
    @DisplayName("한 사람에게 제재와 반려가 섞여 있으면 완료 목록 한 줄에 둘 다 표시된다")
    void 완료_목록은_제재와_반려를_나눠_센다() throws Exception {
        User second = createUser("reporter2", User.Role.USER);
        reportService.report(reporter.getId(), target.getId(), "SPAM", "도배");
        reportService.report(second.getId(), target.getId(), "ABUSE", "욕설");

        Long firstReportId = reportService.getReportsAgainst(target.getId()).stream()
                .filter(r -> r.getReporterUsername().equals("reporter1"))
                .findFirst().orElseThrow().getId();

        mockMvc.perform(patch("/api/reports/{id}/reject", firstReportId).cookie(adminCookie))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/reports/users/{id}/resolve", target.getId())
                        .param("duration", "7d")
                        .cookie(adminCookie))
                .andExpect(status().isOk());

        List<ReportGroupDto> handled = reportService.getHandledGroups();

        assertThat(handled).hasSize(1);
        assertThat(handled.get(0).getReportCount()).isEqualTo(2L);
        assertThat(handled.get(0).getResolvedCount()).isEqualTo(1L);
        assertThat(handled.get(0).getRejectedCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("대기 중인 신고는 처리 완료 목록에 나오지 않는다")
    void 대기중은_완료_목록에_없다() {
        reportService.report(reporter.getId(), target.getId(), "SPAM", "도배");

        assertThat(reportService.getHandledGroups()).isEmpty();
        assertThat(reportService.getPendingGroups()).hasSize(1);
    }

    @Test
    @DisplayName("관리자가 정지하면 대기 중인 신고가 모두 처리 완료가 된다")
    void 정지하면_신고가_처리된다() throws Exception {
        reportService.report(reporter.getId(), target.getId(), "SPAM", "도배");

        mockMvc.perform(patch("/api/reports/users/{id}/resolve", target.getId())
                        .param("duration", "7d")
                        .cookie(adminCookie))
                .andExpect(status().isOk());

        assertThat(reportRepository.countByStatus(Report.Status.PENDING)).isZero();
        assertThat(reportRepository.countByStatus(Report.Status.RESOLVED)).isEqualTo(1);
        assertThat(userRepository.findById(target.getId()).orElseThrow().isSuspended()).isTrue();
        assertThat(reportService.getPendingGroups()).isEmpty();
    }

    @Test
    @DisplayName("신고를 한 건만 반려해도 나머지는 대기로 남는다")
    void 개별_반려는_나머지에_영향이_없다() throws Exception {
        User second = createUser("reporter2", User.Role.USER);
        reportService.report(reporter.getId(), target.getId(), "SPAM", "도배");
        reportService.report(second.getId(), target.getId(), "ABUSE", "욕설");

        Long firstReportId = reportService.getReportsAgainst(target.getId()).stream()
                .filter(r -> r.getReporterUsername().equals("reporter1"))
                .findFirst().orElseThrow().getId();

        mockMvc.perform(patch("/api/reports/{id}/reject", firstReportId).cookie(adminCookie))
                .andExpect(status().isOk());

        assertThat(reportRepository.countByStatus(Report.Status.REJECTED)).isEqualTo(1);
        assertThat(reportRepository.countByStatus(Report.Status.PENDING)).isEqualTo(1);
    }

    @Test
    @DisplayName("반려된 신고는 신고자의 반려 건수로 집계된다")
    void 반려하면_신고자_이력에_잡힌다() throws Exception {
        reportService.report(reporter.getId(), target.getId(), "SPAM", "도배");

        mockMvc.perform(patch("/api/reports/users/{id}/reject", target.getId())
                        .cookie(adminCookie))
                .andExpect(status().isOk());

        List<ReportDetailDto> reports = reportService.getReportsAgainst(target.getId());

        assertThat(reports).hasSize(1);
        assertThat(reports.get(0).getStatus()).isEqualTo("REJECTED");
        assertThat(reports.get(0).getReporterRecentCount()).isEqualTo(1L);
        assertThat(reports.get(0).getReporterRejectedCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("처리된 뒤에는 같은 대상을 다시 신고할 수 있다")
    void 처리후_재신고_가능() throws Exception {
        reportService.report(reporter.getId(), target.getId(), "SPAM", "도배");

        mockMvc.perform(patch("/api/reports/users/{id}/reject", target.getId())
                        .cookie(adminCookie))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/users/{id}/report", target.getId())
                        .cookie(reporterCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("SPAM", "또 도배")))
                .andExpect(status().isOk());

        assertThat(reportRepository.countByStatus(Report.Status.PENDING)).isEqualTo(1);
    }
}

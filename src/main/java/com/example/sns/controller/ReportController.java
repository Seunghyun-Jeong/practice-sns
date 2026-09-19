package com.example.sns.controller;

import com.example.sns.config.MyUserDetails;
import com.example.sns.dto.ReportCreateRequest;
import com.example.sns.service.ReportService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ReportController {

    private final ReportService reportService;

    /** 유저 신고 */
    @PostMapping("/users/{userId}/report")
    public ResponseEntity<?> report(@PathVariable Long userId,
                                    @RequestBody ReportCreateRequest request,
                                    @AuthenticationPrincipal MyUserDetails user) {
        reportService.report(user.getUserId(), userId, request.getReason(), request.getDetail());
        return ResponseEntity.ok(Map.of("message", "신고가 접수되었습니다."));
    }

    /** 신고를 받아들여 정지 + 대기 신고 일괄 처리 */
    @PatchMapping("/reports/users/{userId}/resolve")
    public ResponseEntity<?> resolveAll(@PathVariable Long userId,
                                        @RequestParam String duration) {
        reportService.resolveAll(userId, duration);
        return ResponseEntity.ok(Map.of("message", "정지 처리했습니다."));
    }

    /** 해당 유저에 대한 대기 신고 일괄 반려 */
    @PatchMapping("/reports/users/{userId}/reject")
    public ResponseEntity<?> rejectAll(@PathVariable Long userId) {
        reportService.rejectAll(userId);
        return ResponseEntity.ok(Map.of("message", "신고를 반려했습니다."));
    }

    /** 신고 한 건만 반려 */
    @PatchMapping("/reports/{reportId}/reject")
    public ResponseEntity<?> rejectOne(@PathVariable Long reportId) {
        reportService.rejectOne(reportId);
        return ResponseEntity.ok(Map.of("message", "신고를 반려했습니다."));
    }
}

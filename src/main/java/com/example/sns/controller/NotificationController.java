package com.example.sns.controller;

import com.example.sns.config.MyUserDetails;
import com.example.sns.service.BadgeService;
import com.example.sns.service.NotificationService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    private final BadgeService badgeService;

    /** 최근 알림 목록 */
    @GetMapping
    public ResponseEntity<?> getNotifications(@AuthenticationPrincipal MyUserDetails user) {
        return ResponseEntity.ok(Map.of("notifications", notificationService.getRecent(user.getUserId())));
    }

    /**
     * 안 읽은 알림 수 (헤더 배지).
     *
     * 페이지를 열 때는 헤더가 숫자를 같이 그리므로 이 주소를 부르지 않는다.
     * 실시간 연결이 끊겼다 다시 이어졌을 때, 그 사이 놓친 숫자를 맞추는 데 쓴다.
     * 로그인하지 않은 사람에게는 0을 준다. 탈퇴한 계정의 토큰은 인증 필터에서 걸러져 여기까지 로그인한 채로 오지 않는다.
     */
    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(@AuthenticationPrincipal MyUserDetails user) {
        if (user == null) {
            return ResponseEntity.ok(Map.of("count", 0));
        }
        return ResponseEntity.ok(Map.of("count", badgeService.unreadNotifications(user.getUserId())));
    }

    /** 전체 읽음 처리 */
    @PostMapping("/read")
    public ResponseEntity<?> markAllAsRead(@AuthenticationPrincipal MyUserDetails user) {
        notificationService.markAllAsRead(user.getUserId());
        return ResponseEntity.ok(Map.of("message", "읽음 처리되었습니다."));
    }
}

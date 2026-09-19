package com.example.sns.controller;

import com.example.sns.config.MyUserDetails;
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

    /** 최근 알림 목록 */
    @GetMapping
    public ResponseEntity<?> getNotifications(@AuthenticationPrincipal MyUserDetails user) {
        return ResponseEntity.ok(Map.of("notifications", notificationService.getRecent(user.getUserId())));
    }

    /**
     * 안 읽은 알림 수 (헤더 배지).
     *
     * 로그인하지 않은 사람에게는 0을 준다. 예전에는 유저를 못 찾는 예외까지 여기서 잡아
     * 0으로 바꿔 보냈는데, 그러면 탈퇴한 계정의 토큰으로 들어와도 화면이 정상으로 보였다.
     * 그런 요청은 404로 나가고 종 아이콘은 그대로 두는 편이 상태를 덜 속인다.
     */
    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(@AuthenticationPrincipal MyUserDetails user) {
        if (user == null) {
            return ResponseEntity.ok(Map.of("count", 0));
        }
        return ResponseEntity.ok(Map.of("count", notificationService.getUnreadCount(user.getUserId())));
    }

    /** 전체 읽음 처리 */
    @PostMapping("/read")
    public ResponseEntity<?> markAllAsRead(@AuthenticationPrincipal MyUserDetails user) {
        notificationService.markAllAsRead(user.getUserId());
        return ResponseEntity.ok(Map.of("message", "읽음 처리되었습니다."));
    }
}

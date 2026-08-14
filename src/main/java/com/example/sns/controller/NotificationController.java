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
        try {
            return ResponseEntity.ok(Map.of("notifications", notificationService.getRecent(user.getUserId())));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** 안 읽은 알림 수 (헤더 배지) — 비로그인은 0 */
    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(@AuthenticationPrincipal MyUserDetails user) {
        if (user == null) {
            return ResponseEntity.ok(Map.of("count", 0));
        }
        try {
            return ResponseEntity.ok(Map.of("count", notificationService.getUnreadCount(user.getUserId())));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(Map.of("count", 0));
        }
    }

    /** 전체 읽음 처리 */
    @PostMapping("/read")
    public ResponseEntity<?> markAllAsRead(@AuthenticationPrincipal MyUserDetails user) {
        try {
            notificationService.markAllAsRead(user.getUserId());
            return ResponseEntity.ok(Map.of("message", "읽음 처리되었습니다."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}

package com.example.sns.controller;

import com.example.sns.service.NotificationService;
import com.example.sns.util.JwtUtil;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    private final JwtUtil jwtUtil;

    /** 최근 알림 목록 */
    @GetMapping
    public ResponseEntity<?> getNotifications(@CookieValue(name = "JWT_TOKEN", required = false) String token) {
        Long userId = currentUserId(token);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요합니다."));
        }
        try {
            return ResponseEntity.ok(Map.of("notifications", notificationService.getRecent(userId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** 안 읽은 알림 수 (헤더 배지) */
    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(@CookieValue(name = "JWT_TOKEN", required = false) String token) {
        Long userId = currentUserId(token);
        if (userId == null) {
            return ResponseEntity.ok(Map.of("count", 0));
        }
        try {
            return ResponseEntity.ok(Map.of("count", notificationService.getUnreadCount(userId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(Map.of("count", 0));
        }
    }

    /** 전체 읽음 처리 */
    @PostMapping("/read")
    public ResponseEntity<?> markAllAsRead(@CookieValue(name = "JWT_TOKEN", required = false) String token) {
        Long userId = currentUserId(token);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요합니다."));
        }
        try {
            notificationService.markAllAsRead(userId);
            return ResponseEntity.ok(Map.of("message", "읽음 처리되었습니다."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private Long currentUserId(String token) {
        if (token == null || !jwtUtil.validateToken(token)) {
            return null;
        }
        return jwtUtil.getUserIdFromToken(token);
    }
}

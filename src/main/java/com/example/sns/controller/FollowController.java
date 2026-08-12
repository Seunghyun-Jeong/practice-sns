package com.example.sns.controller;

import com.example.sns.service.FollowService;
import com.example.sns.util.JwtUtil;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/{userId}/follow")
public class FollowController {
    private final FollowService followService;
    private final JwtUtil jwtUtil;

    /** 팔로우 / 팔로우 취소 */
    @PostMapping
    public ResponseEntity<?> toggleFollow(@PathVariable Long userId,
                                          @CookieValue(name = "JWT_TOKEN", required = false) String token) {
        if (token == null || !jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요합니다."));
        }

        String username = jwtUtil.getUsernameFromToken(token);
        try {
            boolean following = followService.toggleFollow(username, userId);
            return ResponseEntity.ok(Map.of(
                    "following", following,
                    "followerCount", followService.countFollowers(userId)
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** 팔로워 / 팔로잉 수 + 내가 팔로우 중인지 */
    @GetMapping
    public ResponseEntity<?> getFollowInfo(@PathVariable Long userId,
                                           @CookieValue(name = "JWT_TOKEN", required = false) String token) {
        Long currentUserId = null;
        if (token != null && jwtUtil.validateToken(token)) {
            currentUserId = jwtUtil.getUserIdFromToken(token);
        }

        try {
            return ResponseEntity.ok(Map.of(
                    "followerCount", followService.countFollowers(userId),
                    "followingCount", followService.countFollowing(userId),
                    "following", followService.isFollowing(currentUserId, userId)
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}

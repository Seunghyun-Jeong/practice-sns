package com.example.sns.controller;

import com.example.sns.config.MyUserDetails;
import com.example.sns.service.FollowService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/{userId}")
public class FollowController {
    private final FollowService followService;

    /** 팔로우 / 팔로우 취소 */
    @PostMapping("/follow")
    public ResponseEntity<?> toggleFollow(@PathVariable Long userId,
                                          @AuthenticationPrincipal MyUserDetails user) {
        try {
            boolean following = followService.toggleFollow(user.getUsername(), userId);
            return ResponseEntity.ok(Map.of(
                    "following", following,
                    "followerCount", followService.countFollowers(userId)
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** 나를 팔로우하는 사람 목록 */
    @GetMapping("/followers")
    public ResponseEntity<?> getFollowers(@PathVariable Long userId) {
        try {
            return ResponseEntity.ok(Map.of("users", followService.getFollowers(userId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** 내가 팔로우하는 사람 목록 */
    @GetMapping("/following")
    public ResponseEntity<?> getFollowingList(@PathVariable Long userId) {
        try {
            return ResponseEntity.ok(Map.of("users", followService.getFollowingList(userId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** 팔로워 / 팔로잉 수 + 내가 팔로우 중인지 (비로그인도 조회 가능) */
    @GetMapping("/follow")
    public ResponseEntity<?> getFollowInfo(@PathVariable Long userId,
                                           @AuthenticationPrincipal MyUserDetails user) {
        Long currentUserId = user != null ? user.getUserId() : null;

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

package com.example.sns.controller;

import com.example.sns.config.MyUserDetails;
import com.example.sns.dto.FollowPageDto;
import com.example.sns.service.FollowService;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
        boolean following = followService.toggleFollow(user.getUsername(), userId);
        return ResponseEntity.ok(Map.of(
                "following", following,
                "followerCount", followService.countFollowers(userId)
        ));
    }

    /** 나를 팔로우하는 사람 한 묶음. 다음 묶음은 받은 마지막 사람의 followedAt, followId를 넘겨 받는다 */
    @GetMapping("/followers")
    public ResponseEntity<FollowPageDto> getFollowers(@PathVariable Long userId,
                                                      @RequestParam(required = false)
                                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beforeAt,
                                                      @RequestParam(required = false) Long beforeId) {
        return ResponseEntity.ok(followService.getFollowers(userId, beforeAt, beforeId));
    }

    /** 내가 팔로우하는 사람 한 묶음 */
    @GetMapping("/following")
    public ResponseEntity<FollowPageDto> getFollowingList(@PathVariable Long userId,
                                                          @RequestParam(required = false)
                                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beforeAt,
                                                          @RequestParam(required = false) Long beforeId) {
        return ResponseEntity.ok(followService.getFollowingList(userId, beforeAt, beforeId));
    }

    /** 팔로워 / 팔로잉 수 + 내가 팔로우 중인지 (비로그인도 조회 가능) */
    @GetMapping("/follow")
    public ResponseEntity<?> getFollowInfo(@PathVariable Long userId,
                                           @AuthenticationPrincipal MyUserDetails user) {
        Long currentUserId = user != null ? user.getUserId() : null;
        followService.requireUser(userId);

        return ResponseEntity.ok(Map.of(
                "followerCount", followService.countFollowers(userId),
                "followingCount", followService.countFollowing(userId),
                "following", followService.isFollowing(currentUserId, userId)
        ));
    }
}

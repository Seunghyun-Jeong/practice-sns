package com.example.sns.controller;

import com.example.sns.config.MyUserDetails;
import com.example.sns.service.PostLikeService;
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
@RequestMapping("/api/posts/{postId}/like")
public class PostLikeController {
    private final PostLikeService postLikeService;

    @PostMapping
    public ResponseEntity<?> toggleLike(@PathVariable Long postId,
                                        @AuthenticationPrincipal MyUserDetails user) {
        boolean liked = postLikeService.toggleLike(postId, user.getUsername());
        return ResponseEntity.ok(Map.of("liked", liked));
    }

    @GetMapping("/count")
    public ResponseEntity<?> getLikeCount(@PathVariable Long postId) {
        long count = postLikeService.countLikes(postId);
        return ResponseEntity.ok(Map.of("likeCount", count));
    }

    @GetMapping("/me")
    public ResponseEntity<?> hasLiked(@PathVariable Long postId,
                                      @AuthenticationPrincipal MyUserDetails user) {
        boolean hasLiked = postLikeService.hasUserLikedPost(postId, user.getUsername());
        return ResponseEntity.ok(Map.of("liked", hasLiked));
    }
}

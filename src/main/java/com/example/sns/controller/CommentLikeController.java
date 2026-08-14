package com.example.sns.controller;

import com.example.sns.config.MyUserDetails;
import com.example.sns.service.CommentLikeService;
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
@RequestMapping("/api/posts/{postId}/comments/{commentId}/like")
public class CommentLikeController {
    private final CommentLikeService commentLikeService;

    @PostMapping
    public ResponseEntity<?> toggleLike(@PathVariable Long commentId,
                                        @AuthenticationPrincipal MyUserDetails user) {
        boolean liked = commentLikeService.toggleLike(commentId, user.getUsername());
        return ResponseEntity.ok(Map.of("liked", liked));
    }

    @GetMapping("/count")
    public ResponseEntity<?> getLikeCount(@PathVariable Long commentId) {
        long count = commentLikeService.countLikes(commentId);
        return ResponseEntity.ok(Map.of("likeCount", count));
    }

    @GetMapping("/me")
    public ResponseEntity<?> hasLiked(@PathVariable Long commentId,
                                      @AuthenticationPrincipal MyUserDetails user) {
        boolean hasLiked = commentLikeService.hasUserLikedComment(commentId, user.getUsername());
        return ResponseEntity.ok(Map.of("liked", hasLiked));
    }
}

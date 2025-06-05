package com.example.sns.controller;

import com.example.sns.service.CommentLikeService;
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
@RequestMapping("/api/posts/{postId}/comments/{commentId}/like")
public class CommentLikeController {
    private final CommentLikeService commentLikeService;
    private final JwtUtil jwtUtil;

    @PostMapping
    public ResponseEntity<?> toggleLike(@PathVariable Long commentId, @CookieValue(name = "JWT_TOKEN", required = false) String token) {
        if (token == null || !jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
        }

        String username = jwtUtil.getUsernameFromToken(token);
        boolean liked = commentLikeService.toggleLike(commentId, username);

        return ResponseEntity.ok(Map.of("liked", liked));
    }

    @GetMapping("/count")
    public ResponseEntity<?> getLikeCount(@PathVariable Long commentId) {
        long count = commentLikeService.countLikes(commentId);
        return ResponseEntity.ok(Map.of("likeCount", count));
    }

    @GetMapping("/me")
    public ResponseEntity<?> hasLiked(@PathVariable Long commentId, @CookieValue(name = "JWT_TOKEN", required = false) String token) {
        if (token == null || !jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
        }

        String username = jwtUtil.getUsernameFromToken(token);
        boolean hasLiked = commentLikeService.hasUserLikedComment(commentId, username);

        return ResponseEntity.ok(Map.of("liked", hasLiked));
    }
}

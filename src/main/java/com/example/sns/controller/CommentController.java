package com.example.sns.controller;

import com.example.sns.dto.CommentDto;
import com.example.sns.dto.CommentUpdateRequest;
import com.example.sns.service.CommentService;
import com.example.sns.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/posts/{postId}/comments")
public class CommentController {
    private final CommentService commentService;
    private final JwtUtil jwtUtil;

    @PostMapping
    public ResponseEntity<?> addComment(@PathVariable Long postId, @RequestBody CommentDto dto, @CookieValue(name = "JWT_TOKEN", required = false) String token) {
        if (token == null || !jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
        }

        String username = jwtUtil.getUsernameFromToken(token);
        commentService.addComment(postId, dto, username);
        return ResponseEntity.ok("댓글이 등록되었습니다.");
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateComment(@PathVariable Long id, @RequestBody CommentUpdateRequest request, @CookieValue(name = "JWT_TOKEN", required = false) String token) {
        if (token == null || !jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않은 토큰입니다.");
        }
        String username = jwtUtil.getUsernameFromToken(token);
        commentService.updateComment(id, request, username);

        return ResponseEntity.ok("댓글이 수정되었습니다.");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteComment(@PathVariable Long id, @CookieValue(name = "JWT_TOKEN", required = false) String token) {
        String username = jwtUtil.getUsernameFromToken(token);
        commentService.deleteComment(id, username);

        return ResponseEntity.ok("댓글이 삭제되었습니다.");
    }
}

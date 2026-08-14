package com.example.sns.controller;

import com.example.sns.config.MyUserDetails;
import com.example.sns.dto.CommentDto;
import com.example.sns.dto.CommentUpdateRequest;
import com.example.sns.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    @PostMapping
    public ResponseEntity<?> addComment(@PathVariable Long postId, @RequestBody CommentDto dto,
                                        @AuthenticationPrincipal MyUserDetails user) {
        commentService.addComment(postId, dto, user.getUsername());
        return ResponseEntity.ok("댓글이 등록되었습니다.");
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateComment(@PathVariable Long id, @RequestBody CommentUpdateRequest request,
                                           @AuthenticationPrincipal MyUserDetails user) {
        commentService.updateComment(id, request, user.getUsername());
        return ResponseEntity.ok("댓글이 수정되었습니다.");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteComment(@PathVariable Long id,
                                           @AuthenticationPrincipal MyUserDetails user) {
        try {
            commentService.deleteComment(id, user.getUsername(), user.getRole());
            return ResponseEntity.ok("댓글이 삭제외었습니다.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }
}

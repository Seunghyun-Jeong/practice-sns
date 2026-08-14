package com.example.sns.controller;

import com.example.sns.config.MyUserDetails;
import com.example.sns.dto.PostResponse;
import com.example.sns.dto.PostUpdateRequest;
import com.example.sns.service.PostService;
import java.util.Map;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {
    private final PostService postService;

    @PostMapping
    public ResponseEntity<?> createPost(@RequestParam("content") String content,
                                        @RequestParam(value = "image", required = false) MultipartFile image,
                                        @AuthenticationPrincipal MyUserDetails user) {
        try {
            PostResponse post = postService.createPost(content, image, user.getUsername());
            return ResponseEntity.ok(post);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePost(@PathVariable Long id,
                                        @AuthenticationPrincipal MyUserDetails user) {
        try {
            postService.deletePost(id, user.getUsername(), user.getRole());
            return ResponseEntity.ok("삭제 완료");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updatePost(@PathVariable Long id,
                                        @RequestBody PostUpdateRequest request,
                                        @AuthenticationPrincipal MyUserDetails user) {
        try {
            postService.updatePost(id, request, user.getUsername());
            return ResponseEntity.ok("수정 완료");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }
}

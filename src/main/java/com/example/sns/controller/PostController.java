package com.example.sns.controller;

import com.example.sns.dto.PostDetailDto;
import com.example.sns.dto.PostRequest;
import com.example.sns.dto.PostResponse;
import com.example.sns.service.PostService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
public class PostController {
    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<PostDetailDto> getPostDetail(@PathVariable Long id) {
        PostDetailDto post = postService.getPostDetail(id);
        if (post != null) {
            return ResponseEntity.ok(post);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<PostResponse> createPost(@RequestBody PostRequest postRequest, @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        PostResponse post = postService.createPost(postRequest, token);
        return ResponseEntity.ok(post);
    }

    @GetMapping
    public ResponseEntity<List<PostResponse>> getAllPosts() {
        return ResponseEntity.ok(postService.getAllPosts());
    }
}

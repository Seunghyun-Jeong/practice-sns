package com.example.sns.controller;

import com.example.sns.dto.PostDetailDto;
import com.example.sns.dto.PostResponse;
import com.example.sns.dto.PostSummaryDto;
import com.example.sns.service.PostService;
import com.example.sns.util.JwtUtil;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class ViewController {

    private final PostService postService;
    private final JwtUtil jwtUtil;

    public ViewController(PostService postService, JwtUtil jwtUtil) {
        this.postService = postService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/signup")
    public String signupPage() {
        return "signup";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/post")
    public String postPage() {
        return "post";
    }

    @GetMapping("/")
    public String mainPage(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        String currentUsername = "게스트";
        if (userDetails != null && userDetails.getUsername() != null) {
            currentUsername = userDetails.getUsername();
        }

        List<PostSummaryDto> posts = postService.getPostsummaries();

        model.addAttribute("username", currentUsername);
        model.addAttribute("posts", posts);

        return "main";
    }

    @GetMapping("/posts/{id}")
    public String postDetail(@PathVariable Long id, Model model) {
        PostDetailDto post = postService.getPostDetail(id);
        model.addAttribute("post", post);
        return "postDetail";
    }

    @GetMapping("/posts/{id}/modal")
    public String postDetailModal(@PathVariable Long id, Model model,
                                  @CookieValue(value = "JWT_TOKEN", required = false) String token) {
        PostDetailDto post = postService.getPostDetail(id);
        Long currentUserId = null;
        if (token != null && jwtUtil.validateToken(token)) {
            currentUserId = jwtUtil.getUserIdFromToken(token);
        }

        model.addAttribute("post", post);
        model.addAttribute("currentUserId", currentUserId);

        return "fragments/postDetailModal :: modalContent";
    }

    @GetMapping("/posts")
    public String allPostsPage(Model model) {
        List<PostResponse> posts = postService.getAllPosts();
        model.addAttribute("posts", posts);
        return "posts";
    }
}

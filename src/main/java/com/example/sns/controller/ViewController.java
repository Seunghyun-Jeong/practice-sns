package com.example.sns.controller;

import com.example.sns.dto.PostDetailDto;
import com.example.sns.dto.PostResponse;
import com.example.sns.dto.PostSummaryDto;
import com.example.sns.dto.UserProfileDto;
import com.example.sns.repository.UserRepository;
import com.example.sns.service.PostService;
import com.example.sns.service.UserService;
import com.example.sns.util.JwtUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
public class ViewController {
    private final PostService postService;
    private final JwtUtil jwtUtil;
    private final UserService userService;

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
        String currentUsername = null;
        if (userDetails != null && userDetails.getUsername() != null) {
            currentUsername = userDetails.getUsername();
        }

        List<PostSummaryDto> posts = postService.getPostsummaries();

        model.addAttribute("username", currentUsername);
        model.addAttribute("posts", posts);

        return "main";
    }

    @GetMapping("/posts/{id}/modal")
    public String postDetailModal(@PathVariable Long id, Model model,
                                  @CookieValue(value = "JWT_TOKEN", required = false) String token) {
        Long currentUserId = null;
        String currentUserRole = "USER";

        if (token != null && jwtUtil.validateToken(token)) {
            currentUserId = jwtUtil.getUserIdFromToken(token);

            String roleFromToken = jwtUtil.getUserRoleFromToken(token);
            if (roleFromToken != null) {
                currentUserRole = roleFromToken;
            }
        }

        PostDetailDto post = postService.getPostDetail(id, currentUserId);
        model.addAttribute("post", post);
        model.addAttribute("currentUserId", currentUserId);
        model.addAttribute("currentUserRole", currentUserRole);  // 여기에 추가!

        return "fragments/postDetailModal :: modalContent";
    }

    @GetMapping("/profile/{username}")
    public String getProfilePage(@PathVariable String username, Model model, @CookieValue("JWT_TOKEN") String token) {
        UserProfileDto profile = userService.getProfile(username);
        List<PostSummaryDto> posts = postService.getPostsByUsernameWithExtras(username);

        model.addAttribute("profileUsername", profile.getUsername());
        model.addAttribute("profileImageUrl", profile.getProfileImageUrl());
        model.addAttribute("myPosts", posts);

        if (token != null && jwtUtil.validateToken(token)) {
            String currentUsername = jwtUtil.getUsernameFromToken(token);
            model.addAttribute("currentUsername", currentUsername);
        }

        return "profile";
    }
}

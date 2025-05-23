package com.example.sns.controller;

import com.example.sns.dto.PostSummaryDto;
import com.example.sns.service.PostService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class ViewController {

    private final PostService postService;

    public ViewController(PostService postService) {
        this.postService = postService;
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
        System.out.println("--------------------------------- userDetails = " + userDetails + " ---------------------------------");

        return "main";
    }

    @GetMapping("/posts/{id}")
    public String postDetail(@PathVariable Long id, Model model) {
        model.addAttribute("post", postService.getPostDetail(id));
        return "postDetail";
    }

    @GetMapping("/posts/{id}/modal")
    public String postDetailModal(@PathVariable Long id, Model model) {
        model.addAttribute("post", postService.getPostDetail(id));
        return "fragments/postDetailModal :: modalContent";
    }
}

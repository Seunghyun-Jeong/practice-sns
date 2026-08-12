package com.example.sns.controller;

import com.example.sns.dto.PostDetailDto;
import com.example.sns.dto.PostSummaryDto;
import com.example.sns.dto.UserProfileDto;
import com.example.sns.repository.UserRepository;
import com.example.sns.service.CommentService;
import com.example.sns.service.FollowService;
import com.example.sns.service.PostService;
import com.example.sns.service.UserService;
import com.example.sns.util.JwtUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class ViewController {
    private final PostService postService;
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final CommentService commentService;
    private final FollowService followService;

    @GetMapping("/signup")
    public String signupPage() {
        return "signup";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/post")
    public String postPage(@CookieValue(value = "JWT_TOKEN", required = false) String token) {
        if (token == null || !jwtUtil.validateToken(token)) {
            return "redirect:/login";
        }
        return "post";
    }

    @GetMapping("/")
    public String mainPage(Model model,
                           @RequestParam(value = "tab", required = false, defaultValue = "all") String tab,
                           @CookieValue(value = "JWT_TOKEN", required = false) String token) {
        Long currentUserId = null;
        if (token != null && jwtUtil.validateToken(token)) {
            currentUserId = jwtUtil.getUserIdFromToken(token);
        }

        // 비로그인 상태에서는 팔로잉 탭을 볼 수 없으므로 전체 탭으로 되돌린다
        boolean followingTab = "following".equals(tab) && currentUserId != null;

        List<PostSummaryDto> posts = followingTab
                ? postService.getFollowingFeed(currentUserId)
                : postService.getPostsummaries(currentUserId);

        model.addAttribute("posts", posts);
        model.addAttribute("tab", followingTab ? "following" : "all");
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
        model.addAttribute("currentUserRole", currentUserRole);

        return "fragments/postDetailModal :: modalContent";
    }

    @GetMapping("/profile/{userId}")
    public String getProfilePage(@PathVariable Long userId, Model model,
                                 @CookieValue(value = "JWT_TOKEN", required = false) String token) {
        UserProfileDto profile = userService.getProfileById(userId);

        String currentUserRole = "USER";
        Long currentUserId = null;
        if (token != null && jwtUtil.validateToken(token)) {
            currentUserId = jwtUtil.getUserIdFromToken(token);
            String roleFromToken = jwtUtil.getUserRoleFromToken(token);
            if (roleFromToken != null) {
                currentUserRole = roleFromToken;
            }
        }

        List<PostSummaryDto> posts = postService.getPostsByUserIdWithExtras(userId, currentUserId);

        model.addAttribute("profileUsername", profile.getUsername());
        model.addAttribute("profileImageUrl", profile.getProfileImageUrl());
        model.addAttribute("myPosts", posts);
        model.addAttribute("profileSuspended", profile.isSuspended());
        model.addAttribute("currentUserRole", currentUserRole);
        model.addAttribute("profileUserId", userId);

        // 팔로우 정보
        model.addAttribute("followerCount", followService.countFollowers(userId));
        model.addAttribute("followingCount", followService.countFollowing(userId));
        model.addAttribute("isFollowing", followService.isFollowing(currentUserId, userId));

        return "profile";
    }

    @GetMapping("/admin/suspended-users")
    public String suspendedUsersPage(Model model,
                                     @CookieValue(value = "JWT_TOKEN", required = false) String token) {
        if (token == null || !jwtUtil.validateToken(token)
                || !"ADMIN".equals(jwtUtil.getUserRoleFromToken(token))) {
            return "redirect:/";
        }

        model.addAttribute("suspendedUsers", userService.getSuspendedUsers());
        return "suspended-users";
    }

    @GetMapping("/admin/users/{userId}/content")
    public String adminUserContent(@PathVariable Long userId, Model model,
                                   @CookieValue(value = "JWT_TOKEN", required = false) String token) {
        if (token == null || !jwtUtil.validateToken(token)
                || !"ADMIN".equals(jwtUtil.getUserRoleFromToken(token))) {
            return "redirect:/";
        }

        UserProfileDto profile = userService.getProfileById(userId);
        model.addAttribute("targetUsername", profile.getUsername());
        model.addAttribute("targetUserId", userId);
        model.addAttribute("posts", postService.getPostsByUserIdWithExtras(userId, null));
        model.addAttribute("comments", commentService.getCommentsByUser(userId));

        return "admin-user-content";
    }
}
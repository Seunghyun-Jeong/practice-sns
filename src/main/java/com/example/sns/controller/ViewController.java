package com.example.sns.controller;

import com.example.sns.config.MyUserDetails;
import com.example.sns.dto.FeedPageDto;
import com.example.sns.dto.PostDetailDto;
import com.example.sns.dto.PostSummaryDto;
import com.example.sns.dto.UserProfileDto;
import com.example.sns.service.CommentService;
import com.example.sns.service.FollowService;
import com.example.sns.service.PostService;
import com.example.sns.service.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * SSR 페이지 컨트롤러.
 * 인증 정보는 JwtAuthFilter가 세팅한 SecurityContext에서 받는다.
 * API(401 JSON)와 달리 페이지는 로그인 화면으로 리다이렉트해야 하므로 여기서 분기한다.
 */
@Controller
@RequiredArgsConstructor
public class ViewController {
    private final PostService postService;
    private final UserService userService;
    private final CommentService commentService;
    private final FollowService followService;

    /** 피드 한 페이지에 보여줄 게시글 수 */
    private static final int FEED_PAGE_SIZE = 10;

    @GetMapping("/signup")
    public String signupPage() {
        return "signup";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/post")
    public String postPage(@AuthenticationPrincipal MyUserDetails user) {
        if (user == null) {
            return "redirect:/login";
        }
        return "post";
    }

    @GetMapping("/")
    public String mainPage(Model model,
                           @RequestParam(value = "tab", required = false, defaultValue = "all") String tab,
                           @RequestParam(value = "tag", required = false) String tag,
                           @AuthenticationPrincipal MyUserDetails user) {
        Long currentUserId = user != null ? user.getUserId() : null;

        // 비로그인 상태에서는 팔로잉 탭을 볼 수 없으므로 전체 탭으로 되돌린다
        boolean followingTab = "following".equals(tab) && currentUserId != null;
        boolean tagFeed = tag != null && !tag.isBlank();

        FeedPageDto feed;
        if (tagFeed) {
            feed = postService.getFeedPageByTag(tag, currentUserId, 0, FEED_PAGE_SIZE);
        } else if (followingTab) {
            feed = postService.getFollowingFeedPage(currentUserId, 0, FEED_PAGE_SIZE);
        } else {
            feed = postService.getFeedPage(currentUserId, 0, FEED_PAGE_SIZE);
        }

        model.addAttribute("posts", feed.getPosts());
        model.addAttribute("hasNext", feed.isHasNext());
        model.addAttribute("tab", followingTab ? "following" : "all");
        model.addAttribute("tag", tagFeed ? tag.trim().toLowerCase() : null);
        return "main";
    }

    /** 무한 스크롤: 다음 페이지의 게시글 카드만 HTML 조각으로 반환 */
    @GetMapping("/feed")
    public String feedPage(Model model,
                           @RequestParam(value = "tab", required = false, defaultValue = "all") String tab,
                           @RequestParam(value = "tag", required = false) String tag,
                           @RequestParam(value = "page", defaultValue = "0") int page,
                           @AuthenticationPrincipal MyUserDetails user) {
        Long currentUserId = user != null ? user.getUserId() : null;

        boolean followingTab = "following".equals(tab) && currentUserId != null;
        boolean tagFeed = tag != null && !tag.isBlank();

        FeedPageDto feed;
        if (tagFeed) {
            feed = postService.getFeedPageByTag(tag, currentUserId, page, FEED_PAGE_SIZE);
        } else if (followingTab) {
            feed = postService.getFollowingFeedPage(currentUserId, page, FEED_PAGE_SIZE);
        } else {
            feed = postService.getFeedPage(currentUserId, page, FEED_PAGE_SIZE);
        }

        model.addAttribute("posts", feed.getPosts());
        model.addAttribute("hasNext", feed.isHasNext());
        return "fragments/postCards :: cards";
    }

    @GetMapping("/posts/{id}/modal")
    public String postDetailModal(@PathVariable Long id, Model model,
                                  @AuthenticationPrincipal MyUserDetails user) {
        Long currentUserId = user != null ? user.getUserId() : null;
        String currentUserRole = user != null ? user.getRole() : "USER";

        PostDetailDto post = postService.getPostDetail(id, currentUserId);
        model.addAttribute("post", post);
        model.addAttribute("currentUserId", currentUserId);
        model.addAttribute("currentUserRole", currentUserRole);

        return "fragments/postDetailModal :: modalContent";
    }

    @GetMapping("/profile/{userId}")
    public String getProfilePage(@PathVariable Long userId, Model model,
                                 @AuthenticationPrincipal MyUserDetails user) {
        UserProfileDto profile = userService.getProfileById(userId);

        Long currentUserId = user != null ? user.getUserId() : null;
        String currentUserRole = user != null ? user.getRole() : "USER";

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
                                     @AuthenticationPrincipal MyUserDetails user) {
        if (user == null || !"ADMIN".equals(user.getRole())) {
            return "redirect:/";
        }

        model.addAttribute("suspendedUsers", userService.getSuspendedUsers());
        return "suspended-users";
    }

    @GetMapping("/admin/users/{userId}/content")
    public String adminUserContent(@PathVariable Long userId, Model model,
                                   @AuthenticationPrincipal MyUserDetails user) {
        if (user == null || !"ADMIN".equals(user.getRole())) {
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

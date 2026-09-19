package com.example.sns.controller;

import com.example.sns.config.MyUserDetails;
import com.example.sns.dto.FeedPageDto;
import com.example.sns.dto.PostDetailDto;
import com.example.sns.dto.PostSummaryDto;
import com.example.sns.dto.ReportDetailDto;
import com.example.sns.dto.UserProfileDto;
import com.example.sns.entity.Report;
import com.example.sns.exception.ForbiddenException;
import com.example.sns.exception.NotFoundException;
import com.example.sns.service.ChatService;
import com.example.sns.service.CommentService;
import com.example.sns.service.FollowService;
import com.example.sns.service.PostService;
import com.example.sns.service.ReportService;
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
    private final ChatService chatService;
    private final FollowService followService;
    private final ReportService reportService;

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
        model.addAttribute("firstPage", true);
        return "main";
    }

    /**
     * 게시글 카드만 HTML 조각으로 반환.
     * 무한 스크롤의 다음 페이지와, 탭 전환 시 첫 페이지(page=0) 교체에 같이 쓴다.
     * 탭 전환이 페이지 전체를 다시 불러오면 배경 별자리까지 새로 뽑히기 때문에 피드만 바꾼다.
     */
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
        model.addAttribute("tab", followingTab ? "following" : "all");
        model.addAttribute("tag", tagFeed ? tag.trim().toLowerCase() : null);
        // 빈 안내문은 첫 페이지에만. 다음 페이지가 비어 있는 것은 끝에 닿은 것이지 팔로우가 없는 게 아니다.
        model.addAttribute("firstPage", page == 0);
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

        // 신고 모달의 사유 드롭다운
        model.addAttribute("reportReasons", Report.Reason.values());

        return "profile";
    }

    /*
     * 아래 /admin 페이지들은 권한 검사를 여기서 하지 않는다.
     * SecurityConfig가 /admin/** 을 ADMIN 전용으로 선언하고 있고,
     * 컨트롤러마다 같은 검사를 복사해두면 새 페이지에서 빠뜨려도 드러나지 않는다.
     */
    @GetMapping("/admin/suspended-users")
    public String suspendedUsersPage(Model model) {
        model.addAttribute("suspendedUsers", userService.getSuspendedUsers());
        return "suspended-users";
    }

    /**
     * 관리자 신고 목록: 신고 한 건이 아니라 피신고자 한 명이 한 줄이다.
     * 대기 중과 처리 완료는 탭으로 나누고, 탭 상태는 쿼리에 남겨
     * 뒤로가기와 새로고침이 그대로 동작하게 한다.
     */
    @GetMapping("/admin/reports")
    public String reportsPage(Model model,
                              @RequestParam(value = "tab", required = false, defaultValue = "pending") String tab) {
        boolean handledTab = "handled".equals(tab);
        model.addAttribute("tab", handledTab ? "handled" : "pending");
        model.addAttribute("reportGroups",
                handledTab ? reportService.getHandledGroups() : reportService.getPendingGroups());
        return "admin-reports";
    }

    /** 관리자 신고 상세: 그 유저가 받은 신고를 한 건씩 */
    @GetMapping("/admin/reports/users/{userId}")
    public String reportDetailPage(@PathVariable Long userId, Model model) {
        UserProfileDto profile = userService.getProfileById(userId);
        List<ReportDetailDto> reports = reportService.getReportsAgainst(userId);

        model.addAttribute("targetUsername", profile.getUsername());
        model.addAttribute("targetUserId", userId);
        model.addAttribute("targetSuspended", profile.isSuspended());
        model.addAttribute("reports", reports);
        model.addAttribute("historyDays", reportService.getReporterHistoryDays());

        /*
         * 전체 반려는 대기 중인 신고를 대상으로 하므로 대기 건이 없으면 할 일이 없다.
         * 숨기지 않고 비활성화하는 이유는 버튼을 빼면 오른쪽 묶음이 짧아지면서
         * 옆의 정지 버튼 위치가 화면마다 달라지기 때문이다.
         * 정지는 대기 건과 무관하게(정지 기간이 끝난 유저를 다시 정지) 쓸 수 있어 그대로 둔다.
         */
        model.addAttribute("hasPending", reports.stream().anyMatch(ReportDetailDto::isPending));

        return "admin-report-detail";
    }

    @GetMapping("/admin/users/{userId}/content")
    public String adminUserContent(@PathVariable Long userId, Model model) {
        UserProfileDto profile = userService.getProfileById(userId);
        model.addAttribute("targetUsername", profile.getUsername());
        model.addAttribute("targetUserId", userId);
        model.addAttribute("posts", postService.getPostsByUserIdWithExtras(userId, null));
        model.addAttribute("comments", commentService.getCommentsByUser(userId));

        return "admin-user-content";
    }

    @GetMapping("/chat")
    public String chatListPage(@AuthenticationPrincipal MyUserDetails user) {
        if (user == null) {
            return "redirect:/login";
        }
        return "chat";
    }

    @GetMapping("/chat/{roomId}")
    public String chatRoomPage(@PathVariable Long roomId, Model model,
                               @AuthenticationPrincipal MyUserDetails user) {
        if (user == null) {
            return "redirect:/login";
        }
        try {
            model.addAttribute("room", chatService.getRoom(user.getUserId(), roomId));
        } catch (NotFoundException | ForbiddenException e) {
            // 없는 방이거나 내 방이 아니면 목록으로 돌려보낸다.
            // 화면 요청이라 JSON을 줄 수 없으니 여기서만 직접 잡는다.
            return "redirect:/chat";
        }
        return "chatRoom";
    }
}

package com.example.sns.controller;

import com.example.sns.config.MyUserDetails;
import com.example.sns.dto.FeedQuery;
import com.example.sns.service.PostService;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/feed")
public class FeedController {
    private final PostService postService;

    /**
     * 피드 화면 맨 위 글보다 나중에 쓴 글이 있는지.
     * 피드를 보는 동안 화면이 주기적으로 묻고, 있으면 새 게시물 버튼을 띄운다.
     */
    @GetMapping("/new")
    public ResponseEntity<Map<String, Boolean>> hasNewPosts(
            @RequestParam(value = "tab", required = false, defaultValue = "all") String tab,
            @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime afterAt,
            @RequestParam(required = false) Long afterId,
            @AuthenticationPrincipal MyUserDetails user) {
        FeedQuery query = FeedQuery.of(tab, tag, null, user != null ? user.getUserId() : null);
        return ResponseEntity.ok(Map.of("hasNew", postService.hasNewPosts(query, afterAt, afterId)));
    }
}

package com.example.sns.controller;

import com.example.sns.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/search")
public class SearchController {
    private final SearchService searchService;

    /** 아이디 / 해시태그 통합 검색 */
    @GetMapping
    public ResponseEntity<?> search(@RequestParam(value = "q", required = false) String q) {
        return ResponseEntity.ok(searchService.search(q));
    }
}

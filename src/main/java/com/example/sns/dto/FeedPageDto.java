package com.example.sns.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 피드 한 페이지 */
@Getter
@AllArgsConstructor
public class FeedPageDto {
    private List<PostSummaryDto> posts;
    private int page;
    private boolean hasNext;
}

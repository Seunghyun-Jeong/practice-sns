package com.example.sns.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 댓글 한 묶음 (원 댓글 또는 한 원 댓글의 답글) */
@Getter
@AllArgsConstructor
public class CommentPageDto {
    private List<CommentDto> comments;
    private boolean hasNext;
}

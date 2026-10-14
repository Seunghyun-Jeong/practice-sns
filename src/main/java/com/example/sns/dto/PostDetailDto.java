package com.example.sns.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PostDetailDto {
    private Long id;
    private String content;
    private String imageUrl;
    private String author;
    private Long authorId;
    private String authorProfileImageUrl;
    private String createdAt;
    private String updatedAt;
    private long likeCount;
    private long commentCount;
    /** 원 댓글 첫 묶음. 나머지는 스크롤하면서 이어서 불러온다 */
    private List<CommentDto> comments;
    private boolean hasMoreComments;
    private boolean likedByCurrentUser;
    private boolean suspended;
}

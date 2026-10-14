package com.example.sns.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CommentDto {
    private Long id;
    private String author;
    private Long authorId;
    private String authorProfileImageUrl;
    private String content;
    private String createdAt;
    private String updatedAt;
    private long likeCount;
    private boolean likedByCurrentUser;
    private boolean suspended;

    /** 답글이면 원 댓글의 id. 댓글을 등록할 때 클라이언트가 보내기도 한다 */
    private Long parentId;

    /** 원 댓글에 달린 답글 수. 답글은 접어 두었다가 펼칠 때 따로 불러온다 */
    private long replyCount;

    /** 본문에 언급된 사람들. 화면에서 프로필 링크를 만들 때 쓴다 */
    private List<MentionDto> mentions = new ArrayList<>();
}

package com.example.sns.dto;

/**
 * 어떤 피드를 볼지.
 *
 * 피드를 고르는 규칙(작성자가 정해지면 그 사람의 글, 태그가 있으면 태그 피드, 로그인해야 팔로잉 피드,
 * 태그는 소문자)을 피드 화면, 프로필, 다음 묶음, 새 글 확인이 모두 같이 써야 해서 여기 한 곳에서만 정한다.
 *
 * @param authorId 작성자 피드(프로필)일 때 그 사람
 * @param userId   보는 사람 (팔로잉 피드의 기준, 내 좋아요 여부)
 */
public record FeedQuery(Kind kind, String tag, Long authorId, Long userId) {

    public enum Kind { ALL, FOLLOWING, TAG, AUTHOR }

    public static FeedQuery of(String tab, String tag, Long authorId, Long currentUserId) {
        if (authorId != null) {
            return new FeedQuery(Kind.AUTHOR, null, authorId, currentUserId);
        }
        if (tag != null && !tag.isBlank()) {
            return new FeedQuery(Kind.TAG, tag.trim().toLowerCase(), null, currentUserId);
        }
        // 비로그인 상태에서는 팔로잉 탭을 볼 수 없으므로 전체 탭으로 되돌린다
        if ("following".equals(tab) && currentUserId != null) {
            return new FeedQuery(Kind.FOLLOWING, null, null, currentUserId);
        }
        return new FeedQuery(Kind.ALL, null, null, currentUserId);
    }

    /** 화면의 탭 이름. 태그 피드와 프로필은 탭이 없으므로 전체로 둔다 */
    public String tabName() {
        return kind == Kind.FOLLOWING ? "following" : "all";
    }
}

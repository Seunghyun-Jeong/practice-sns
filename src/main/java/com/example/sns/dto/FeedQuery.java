package com.example.sns.dto;

/**
 * 어떤 피드를 볼지.
 *
 * 탭과 태그를 해석하는 규칙(태그가 있으면 태그 피드, 로그인해야 팔로잉 피드, 태그는 소문자)을
 * 피드 화면, 다음 묶음, 새 글 확인이 모두 같이 써야 해서 여기 한 곳에서만 정한다.
 */
public record FeedQuery(Kind kind, String tag, Long userId) {

    public enum Kind { ALL, FOLLOWING, TAG }

    public static FeedQuery of(String tab, String tag, Long currentUserId) {
        if (tag != null && !tag.isBlank()) {
            return new FeedQuery(Kind.TAG, tag.trim().toLowerCase(), currentUserId);
        }
        // 비로그인 상태에서는 팔로잉 탭을 볼 수 없으므로 전체 탭으로 되돌린다
        if ("following".equals(tab) && currentUserId != null) {
            return new FeedQuery(Kind.FOLLOWING, null, currentUserId);
        }
        return new FeedQuery(Kind.ALL, null, currentUserId);
    }

    /** 화면의 탭 이름. 태그 피드는 탭이 없으므로 전체로 둔다 */
    public String tabName() {
        return kind == Kind.FOLLOWING ? "following" : "all";
    }
}

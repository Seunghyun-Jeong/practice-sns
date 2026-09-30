package com.example.sns.util;

/**
 * 게시글, 댓글, DM 본문이 함께 따르는 규칙.
 *
 * 비어 있으면 안 되고, 앞뒤 공백을 뺀 값을 저장하며, 칸 크기를 넘으면 안 된다.
 * 칸 크기는 대상마다 달라서 각 엔티티의 MAX_CONTENT_LENGTH를 받는다.
 * 검사를 서비스마다 따로 두면 한쪽만 바뀌는 일이 생겨서 여기 한 곳에 둔다.
 */
public final class TextContent {

    private TextContent() {
    }

    /** 규칙을 통과하면 앞뒤 공백을 뺀 본문을 돌려준다 */
    public static String require(String content, int maxLength) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("내용을 입력해주세요.");
        }
        String trimmed = content.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException("내용은 " + maxLength + "자까지 쓸 수 있습니다.");
        }
        return trimmed;
    }
}

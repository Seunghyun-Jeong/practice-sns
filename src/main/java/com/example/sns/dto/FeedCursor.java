package com.example.sns.dto;

import java.time.LocalDateTime;

/** 피드에서 글 하나의 자리. 피드는 (작성 시각, id) 순서로 줄 세운다 */
public record FeedCursor(LocalDateTime createdAt, Long id) {

    /** 이 글이 (at, id) 자리보다 나중에 쓴 글인지. 비교할 자리가 없으면(빈 피드) 어떤 글이든 새 글이다 */
    public boolean isAfter(LocalDateTime at, Long otherId) {
        if (at == null) {
            return true;
        }
        return createdAt.isAfter(at) || (createdAt.isEqual(at) && id > otherId);
    }
}

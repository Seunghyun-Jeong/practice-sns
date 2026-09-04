package com.example.sns.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 알림.
 * actor 가 recipient 에게 어떤 행동(type)을 해서 생긴 기록이다.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "notification")
public class Notification {

    public enum Type {
        FOLLOW,      // 나를 팔로우함
        POST_LIKE,   // 내 게시글에 좋아요를 누름
        COMMENT,     // 내 게시글에 댓글을 씀
        MENTION      // 댓글에서 나를 언급했거나 내 댓글에 답글을 달았음
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 알림을 받는 사람 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    /** 알림을 발생시킨 사람 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    /**
     * ENUM이 아니라 문자열 컬럼으로 둔다.
     *
     * MySQL의 ENUM으로 두면 종류를 새로 추가할 때 컬럼에 값을 직접 넣어주기 전까지
     * 저장이 안 된다. ddl-auto=update는 컬럼을 새로 만들어주기만 하고
     * 이미 있는 컬럼의 정의는 바꾸지 않기 때문이다.
     * 실제로 MENTION을 추가하면서 저장이 잘리는 문제를 겪었다.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20, columnDefinition = "varchar(20)")
    private Type type;

    /** 좋아요·댓글 알림이 가리키는 게시글 (팔로우 알림은 없음) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    /** 댓글 알림이 가리키는 댓글 (내용 미리보기에 사용) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id")
    private Comment comment;

    private boolean isRead = false;

    private LocalDateTime createdAt = LocalDateTime.now();
}

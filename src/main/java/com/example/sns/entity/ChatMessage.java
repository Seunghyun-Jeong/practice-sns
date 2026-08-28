package com.example.sns.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** 채팅 메시지. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "chat_message")
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false, length = 1000)
    private String content;

    /** 상대방이 읽었는지 (안읽음 수 계산에 사용) */
    private boolean isRead = false;

    /** 보낸 뒤 수정했는지 ("수정됨" 표시용) */
    private boolean isEdited = false;

    /**
     * 삭제했는지. 행을 지우지 않고 표시만 바꾸는 이유는
     * 그 자리에 "삭제된 메시지입니다"를 그려야 하기 때문이다.
     * 삭제 시 content 는 비운다 (안 읽힌 내용이라 보관할 이유가 없다).
     */
    private boolean isDeleted = false;

    private LocalDateTime createdAt = LocalDateTime.now();
}

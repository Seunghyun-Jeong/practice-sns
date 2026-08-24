package com.example.sns.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 1:1 채팅방.
 * 두 사람 조합당 방은 하나만 존재한다.
 * 어느 쪽이 먼저 열어도 같은 방을 찾을 수 있도록 id가 작은 쪽을 항상 user1에 둔다.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "chat_room",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chat_room_users",
                columnNames = {"user1_id", "user2_id"}
        )
)
public class ChatRoom {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 참여자 중 id가 작은 쪽 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user1_id", nullable = false)
    private User user1;

    /** 참여자 중 id가 큰 쪽 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user2_id", nullable = false)
    private User user2;

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChatMessage> messages = new ArrayList<>();

    private LocalDateTime createdAt = LocalDateTime.now();

    /** 방 목록을 최근 대화 순으로 정렬하기 위한 값 */
    private LocalDateTime lastMessageAt = LocalDateTime.now();

    /** 이 방에 참여 중인 사람인지 */
    public boolean hasParticipant(Long userId) {
        return user1.getId().equals(userId) || user2.getId().equals(userId);
    }

    /** 나를 기준으로 상대방을 돌려준다 */
    public User getPartnerOf(Long userId) {
        return user1.getId().equals(userId) ? user2 : user1;
    }
}

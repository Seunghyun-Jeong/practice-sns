package com.example.sns.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 팔로우 관계.
 * follower 가 following 을 팔로우한다. (같은 조합은 한 번만 존재)
 *
 * 팔로워, 팔로잉 목록은 최근에 팔로우한 순으로 (팔로우한 시각, id)를 기준 삼아
 * "이 다음부터" 끊어 읽으므로 그 순서 그대로 인덱스를 둔다.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "follow",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_follow_follower_following",
                columnNames = {"follower_id", "following_id"}
        ),
        indexes = {
                @Index(name = "idx_follow_following_followed", columnList = "following_id, followedAt, id"),
                @Index(name = "idx_follow_follower_followed", columnList = "follower_id, followedAt, id")
        }
)
public class Follow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 팔로우를 하는 사람 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follower_id", nullable = false)
    private User follower;

    /** 팔로우를 당하는 사람 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "following_id", nullable = false)
    private User following;

    private LocalDateTime followedAt = LocalDateTime.now();
}

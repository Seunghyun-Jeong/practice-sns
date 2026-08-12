package com.example.sns.repository;

import com.example.sns.entity.Notification;
import com.example.sns.entity.Post;
import com.example.sns.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 최근 알림 목록 (행동한 사람과 게시글을 함께 조회해 N+1을 피한다) */
    @Query("SELECT n FROM Notification n "
            + "JOIN FETCH n.actor "
            + "LEFT JOIN FETCH n.post "
            + "LEFT JOIN FETCH n.comment "
            + "WHERE n.recipient = :recipient "
            + "ORDER BY n.createdAt DESC")
    List<Notification> findRecent(@Param("recipient") User recipient, Pageable pageable);

    /** 안 읽은 알림 수 */
    long countByRecipientAndIsReadFalse(User recipient);

    /**
     * 같은 알림이 중복으로 쌓이지 않게 확인 (좋아요 취소 후 재클릭 등).
     * 댓글 알림은 여러 건이 쌓일 수 있으므로 단건이 아닌 목록으로 받는다.
     */
    List<Notification> findByRecipientAndActorAndTypeAndPost(
            User recipient, User actor, Notification.Type type, Post post);

    /** 팔로우 알림처럼 게시글이 없는 경우 */
    List<Notification> findByRecipientAndActorAndTypeAndPostIsNull(
            User recipient, User actor, Notification.Type type);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Notification n SET n.isRead = true "
            + "WHERE n.recipient = :recipient AND n.isRead = false")
    int markAllAsRead(@Param("recipient") User recipient);
}

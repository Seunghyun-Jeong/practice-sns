package com.example.sns.repository;

import com.example.sns.entity.ChatMessage;
import com.example.sns.entity.ChatRoom;
import com.example.sns.entity.User;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** 방의 메시지를 최신순으로 (무한 스크롤용) */
    Slice<ChatMessage> findByRoomOrderByCreatedAtDescIdDesc(ChatRoom room, Pageable pageable);

    /**
     * 방 목록용: 각 방의 마지막 메시지를 쿼리 한 번에 가져온다.
     * 방마다 따로 조회하면 방 수만큼 쿼리가 나가므로(N+1) 묶어서 조회한다.
     */
    @Query("SELECT m FROM ChatMessage m "
            + "WHERE m.id IN (SELECT MAX(m2.id) FROM ChatMessage m2 "
            + "               WHERE m2.room.id IN :roomIds GROUP BY m2.room.id)")
    List<ChatMessage> findLastMessages(@Param("roomIds") Collection<Long> roomIds);

    /** 방 목록용: 방별로 상대가 보냈고 내가 안 읽은 메시지 수 */
    @Query("SELECT m.room.id, COUNT(m) FROM ChatMessage m "
            + "WHERE m.room.id IN :roomIds AND m.sender <> :me AND m.isRead = false "
            + "GROUP BY m.room.id")
    List<Object[]> countUnreadByRoomIds(@Param("roomIds") Collection<Long> roomIds,
                                        @Param("me") User me);

    /**
     * 방에 들어왔을 때 상대가 보낸 메시지를 전부 읽음 처리.
     * 벌크 UPDATE는 영속성 컨텍스트를 거치지 않고 DB로 바로 나가므로,
     * 이미 로드된 엔티티가 옛 값을 들고 있지 않도록 컨텍스트를 비운다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ChatMessage m SET m.isRead = true "
            + "WHERE m.room = :room AND m.sender <> :me AND m.isRead = false")
    int markAllAsRead(@Param("room") ChatRoom room, @Param("me") User me);

    /** 헤더 배지용: 내가 안 읽은 메시지 전체 수 */
    @Query("SELECT COUNT(m) FROM ChatMessage m "
            + "WHERE (m.room.user1 = :me OR m.room.user2 = :me) "
            + "AND m.sender <> :me AND m.isRead = false")
    long countTotalUnread(@Param("me") User me);
}

package com.example.sns.repository;

import com.example.sns.entity.ChatRoom;
import com.example.sns.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    /** 두 사람의 방 찾기 (user1에는 항상 id가 작은 쪽이 들어 있다) */
    Optional<ChatRoom> findByUser1AndUser2(User user1, User user2);

    /** 내가 참여한 방을 최근 대화 순으로. 상대 정보 표시를 위해 참여자를 함께 가져온다 */
    @Query("SELECT r FROM ChatRoom r "
            + "JOIN FETCH r.user1 JOIN FETCH r.user2 "
            + "WHERE r.user1 = :user OR r.user2 = :user "
            + "ORDER BY r.lastMessageAt DESC")
    List<ChatRoom> findAllByParticipant(@Param("user") User user);
}

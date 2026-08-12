package com.example.sns.repository;

import com.example.sns.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByUsername(String username);
    Optional<User> findByUsername(String username);
    List<User> findBySuspendedUntilAfterOrderBySuspendedUntilAsc(LocalDateTime dateTime);

    /** 아이디로 유저 검색 (정지된 유저는 제외) */
    @Query("SELECT u FROM User u "
            + "WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "AND (u.suspendedUntil IS NULL OR u.suspendedUntil <= :now) "
            + "ORDER BY u.username ASC")
    List<User> searchByUsername(@Param("keyword") String keyword,
                                @Param("now") LocalDateTime now,
                                Pageable pageable);
}

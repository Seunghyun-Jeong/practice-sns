package com.example.sns.repository;

import com.example.sns.dto.FollowUserDto;
import com.example.sns.entity.Follow;
import com.example.sns.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowRepository extends JpaRepository<Follow, Long> {
    Optional<Follow> findByFollowerAndFollowing(User follower, User following);
    boolean existsByFollowerAndFollowing(User follower, User following);

    /** 내가 팔로우하는 수 */
    long countByFollower(User follower);

    /** 나를 팔로우하는 수 */
    long countByFollowing(User following);

    /** 내가 팔로우하는 사람들 */
    List<Follow> findAllByFollower(User follower);

    /*
     * 팔로워 / 팔로잉 목록은 화면에 필요한 칸(id, 아이디, 프로필 사진)만 바로 꺼낸다.
     * 엔티티로 읽으면 사람마다 따로 조회되고(지연 로딩), 목록에는 엔티티로 할 일이 없다.
     * 정지된 사람은 쿼리에서 거른다. 자바에서 거르면 한 묶음이 정해진 수보다 적게 나간다.
     * 다음 묶음은 이미 보여준 마지막 사람의 (팔로우한 시각, follow id)보다 먼저 팔로우한 사람부터 읽는다.
     */

    /** 나를 팔로우하는 사람 첫 묶음 (최근에 팔로우한 순) */
    @Query("SELECT new com.example.sns.dto.FollowUserDto(u.id, u.username, u.profileImageUrl, f.followedAt, f.id) "
            + "FROM Follow f JOIN f.follower u "
            + "WHERE f.following.id = :userId AND (u.suspendedUntil IS NULL OR u.suspendedUntil <= :now) "
            + "ORDER BY f.followedAt DESC, f.id DESC")
    Slice<FollowUserDto> findFollowers(@Param("userId") Long userId, @Param("now") LocalDateTime now,
                                       Pageable pageable);

    /** 나를 팔로우하는 사람 다음 묶음 */
    @Query("SELECT new com.example.sns.dto.FollowUserDto(u.id, u.username, u.profileImageUrl, f.followedAt, f.id) "
            + "FROM Follow f JOIN f.follower u "
            + "WHERE f.following.id = :userId AND (u.suspendedUntil IS NULL OR u.suspendedUntil <= :now) "
            + "AND (f.followedAt < :beforeAt OR (f.followedAt = :beforeAt AND f.id < :beforeId)) "
            + "ORDER BY f.followedAt DESC, f.id DESC")
    Slice<FollowUserDto> findFollowersBefore(@Param("userId") Long userId, @Param("now") LocalDateTime now,
                                             @Param("beforeAt") LocalDateTime beforeAt,
                                             @Param("beforeId") Long beforeId, Pageable pageable);

    /** 내가 팔로우하는 사람 첫 묶음 (최근에 팔로우한 순) */
    @Query("SELECT new com.example.sns.dto.FollowUserDto(u.id, u.username, u.profileImageUrl, f.followedAt, f.id) "
            + "FROM Follow f JOIN f.following u "
            + "WHERE f.follower.id = :userId AND (u.suspendedUntil IS NULL OR u.suspendedUntil <= :now) "
            + "ORDER BY f.followedAt DESC, f.id DESC")
    Slice<FollowUserDto> findFollowing(@Param("userId") Long userId, @Param("now") LocalDateTime now,
                                       Pageable pageable);

    /** 내가 팔로우하는 사람 다음 묶음 */
    @Query("SELECT new com.example.sns.dto.FollowUserDto(u.id, u.username, u.profileImageUrl, f.followedAt, f.id) "
            + "FROM Follow f JOIN f.following u "
            + "WHERE f.follower.id = :userId AND (u.suspendedUntil IS NULL OR u.suspendedUntil <= :now) "
            + "AND (f.followedAt < :beforeAt OR (f.followedAt = :beforeAt AND f.id < :beforeId)) "
            + "ORDER BY f.followedAt DESC, f.id DESC")
    Slice<FollowUserDto> findFollowingBefore(@Param("userId") Long userId, @Param("now") LocalDateTime now,
                                             @Param("beforeAt") LocalDateTime beforeAt,
                                             @Param("beforeId") Long beforeId, Pageable pageable);
}

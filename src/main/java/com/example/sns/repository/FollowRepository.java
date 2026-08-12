package com.example.sns.repository;

import com.example.sns.entity.Follow;
import com.example.sns.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowRepository extends JpaRepository<Follow, Long> {
    Optional<Follow> findByFollowerAndFollowing(User follower, User following);
    boolean existsByFollowerAndFollowing(User follower, User following);

    /** 내가 팔로우하는 수 */
    long countByFollower(User follower);

    /** 나를 팔로우하는 수 */
    long countByFollowing(User following);

    /** 내가 팔로우하는 사람들 */
    List<Follow> findAllByFollower(User follower);
}

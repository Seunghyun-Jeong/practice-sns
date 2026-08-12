package com.example.sns.service;

import com.example.sns.dto.FollowUserDto;
import com.example.sns.entity.Follow;
import com.example.sns.entity.User;
import com.example.sns.repository.FollowRepository;
import com.example.sns.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FollowService {
    private final FollowRepository followRepository;
    private final UserRepository userRepository;

    /**
     * 팔로우 토글. 이미 팔로우 중이면 취소한다.
     *
     * @return 토글 후 팔로우 상태 (true = 팔로우 중)
     */
    @Transactional
    public boolean toggleFollow(String followerUsername, Long targetUserId) {
        User follower = userRepository.findByUsername(followerUsername)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("팔로우할 유저를 찾을 수 없습니다."));

        if (follower.getId().equals(target.getId())) {
            throw new IllegalArgumentException("자기 자신은 팔로우할 수 없습니다.");
        }

        if (target.isSuspended()) {
            throw new IllegalArgumentException("이용이 정지된 유저입니다.");
        }

        Optional<Follow> existing = followRepository.findByFollowerAndFollowing(follower, target);
        if (existing.isPresent()) {
            followRepository.delete(existing.get());
            return false;
        }

        Follow follow = new Follow();
        follow.setFollower(follower);
        follow.setFollowing(target);
        followRepository.save(follow);
        return true;
    }

    /** 나를 팔로우하는 사람 수 */
    public long countFollowers(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));
        return followRepository.countByFollowing(user);
    }

    /** 내가 팔로우하는 사람 수 */
    public long countFollowing(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));
        return followRepository.countByFollower(user);
    }

    /** 나를 팔로우하는 사람 목록 (정지 유저 제외) */
    public List<FollowUserDto> getFollowers(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));

        return followRepository.findAllByFollowingOrderByFollowedAtDesc(user).stream()
                .map(Follow::getFollower)
                .filter(u -> !u.isSuspended())
                .map(this::toFollowUserDto)
                .collect(Collectors.toList());
    }

    /** 내가 팔로우하는 사람 목록 (정지 유저 제외) */
    public List<FollowUserDto> getFollowingList(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));

        return followRepository.findAllByFollowerOrderByFollowedAtDesc(user).stream()
                .map(Follow::getFollowing)
                .filter(u -> !u.isSuspended())
                .map(this::toFollowUserDto)
                .collect(Collectors.toList());
    }

    private FollowUserDto toFollowUserDto(User user) {
        return new FollowUserDto(user.getId(), user.getUsername(), user.getProfileImageUrl());
    }

    /** currentUserId 가 targetUserId 를 팔로우 중인지 */
    public boolean isFollowing(Long currentUserId, Long targetUserId) {
        if (currentUserId == null || currentUserId.equals(targetUserId)) {
            return false;
        }
        Optional<User> follower = userRepository.findById(currentUserId);
        Optional<User> target = userRepository.findById(targetUserId);
        if (follower.isEmpty() || target.isEmpty()) {
            return false;
        }
        return followRepository.existsByFollowerAndFollowing(follower.get(), target.get());
    }
}

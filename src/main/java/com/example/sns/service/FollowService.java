package com.example.sns.service;

import com.example.sns.dto.FollowPageDto;
import com.example.sns.dto.FollowUserDto;
import com.example.sns.entity.Follow;
import com.example.sns.entity.Notification;
import com.example.sns.entity.User;
import com.example.sns.event.NotificationEvent;
import com.example.sns.exception.NotFoundException;
import com.example.sns.repository.FollowRepository;
import com.example.sns.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FollowService {
    /** 팔로워 / 팔로잉 목록을 한 번에 불러오는 사람 수 */
    private static final int FOLLOW_PAGE_SIZE = 20;

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 팔로우 토글. 이미 팔로우 중이면 취소한다.
     *
     * @return 토글 후 팔로우 상태 (true = 팔로우 중)
     */
    @Transactional
    public boolean toggleFollow(String followerUsername, Long targetUserId) {
        User follower = userRepository.findByUsername(followerUsername)
                .orElseThrow(() -> new NotFoundException("유저를 찾을 수 없습니다."));

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new NotFoundException("팔로우할 유저를 찾을 수 없습니다."));

        if (follower.getId().equals(target.getId())) {
            throw new IllegalArgumentException("자기 자신은 팔로우할 수 없습니다.");
        }

        if (target.isSuspended()) {
            throw new IllegalArgumentException("이용이 정지된 유저입니다.");
        }

        Optional<Follow> existing = followRepository.findByFollowerAndFollowing(follower, target);
        if (existing.isPresent()) {
            followRepository.delete(existing.get());
            eventPublisher.publishEvent(NotificationEvent.cancelled(target, follower, Notification.Type.FOLLOW, null));
            return false;
        }

        Follow follow = new Follow();
        follow.setFollower(follower);
        follow.setFollowing(target);
        followRepository.save(follow);

        // 팔로우 당한 사람에게 알림
        eventPublisher.publishEvent(NotificationEvent.created(target, follower, Notification.Type.FOLLOW, null));
        return true;
    }

    /*
     * 수와 팔로우 여부는 사람을 엔티티로 읽지 않고 id로 바로 센다.
     * 사람이 있는지는 부르는 쪽이 이미 확인했다(프로필은 프로필 조회, 팔로우 토글은 토글 처리).
     * 따로 물어보는 API는 requireUser로 먼저 확인한다.
     */

    /** 나를 팔로우하는 사람 수 */
    public long countFollowers(Long userId) {
        return followRepository.countByFollowing_Id(userId);
    }

    /** 내가 팔로우하는 사람 수 */
    public long countFollowing(Long userId) {
        return followRepository.countByFollower_Id(userId);
    }

    /**
     * 나를 팔로우하는 사람 한 묶음 (최근에 팔로우한 순, 정지 유저 제외).
     * beforeAt, beforeId가 없으면 첫 묶음이고, 있으면 그 사람보다 먼저 팔로우한 사람부터 이어서 준다.
     */
    public FollowPageDto getFollowers(Long userId, LocalDateTime beforeAt, Long beforeId) {
        requireUser(userId);
        LocalDateTime now = LocalDateTime.now();
        Slice<FollowUserDto> slice = beforeAt == null
                ? followRepository.findFollowers(userId, now, followPage())
                : followRepository.findFollowersBefore(userId, now, beforeAt, beforeId, followPage());
        return new FollowPageDto(slice.getContent(), slice.hasNext());
    }

    /** 내가 팔로우하는 사람 한 묶음 (최근에 팔로우한 순, 정지 유저 제외) */
    public FollowPageDto getFollowingList(Long userId, LocalDateTime beforeAt, Long beforeId) {
        requireUser(userId);
        LocalDateTime now = LocalDateTime.now();
        Slice<FollowUserDto> slice = beforeAt == null
                ? followRepository.findFollowing(userId, now, followPage())
                : followRepository.findFollowingBefore(userId, now, beforeAt, beforeId, followPage());
        return new FollowPageDto(slice.getContent(), slice.hasNext());
    }

    /** 없는 사람의 목록이나 수는 빈 값이 아니라 404로 알린다. 엔티티는 필요 없으므로 있는지만 본다 */
    public void requireUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("유저를 찾을 수 없습니다.");
        }
    }

    private PageRequest followPage() {
        return PageRequest.of(0, FOLLOW_PAGE_SIZE);
    }

    /** currentUserId 가 targetUserId 를 팔로우 중인지 */
    public boolean isFollowing(Long currentUserId, Long targetUserId) {
        if (currentUserId == null || currentUserId.equals(targetUserId)) {
            return false;
        }
        return followRepository.existsByFollower_IdAndFollowing_Id(currentUserId, targetUserId);
    }
}

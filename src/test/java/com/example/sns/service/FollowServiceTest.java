package com.example.sns.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.sns.entity.Follow;
import com.example.sns.entity.User;
import com.example.sns.repository.FollowRepository;
import com.example.sns.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @Mock
    private FollowRepository followRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private FollowService followService;

    private User me;
    private User other;

    @BeforeEach
    void setUp() {
        me = new User();
        me.setId(1L);
        me.setUsername("me");

        other = new User();
        other.setId(2L);
        other.setUsername("other");
    }

    @Test
    @DisplayName("팔로우하지 않은 상대를 누르면 팔로우된다")
    void 팔로우한다() {
        when(userRepository.findByUsername("me")).thenReturn(Optional.of(me));
        when(userRepository.findById(2L)).thenReturn(Optional.of(other));
        when(followRepository.findByFollowerAndFollowing(me, other)).thenReturn(Optional.empty());

        boolean following = followService.toggleFollow("me", 2L);

        assertThat(following).isTrue();
        verify(followRepository).save(any(Follow.class));
    }

    @Test
    @DisplayName("이미 팔로우 중이면 팔로우가 취소된다")
    void 팔로우를_취소한다() {
        Follow existing = new Follow();
        when(userRepository.findByUsername("me")).thenReturn(Optional.of(me));
        when(userRepository.findById(2L)).thenReturn(Optional.of(other));
        when(followRepository.findByFollowerAndFollowing(me, other)).thenReturn(Optional.of(existing));

        boolean following = followService.toggleFollow("me", 2L);

        assertThat(following).isFalse();
        verify(followRepository).delete(existing);
    }

    @Test
    @DisplayName("자기 자신은 팔로우할 수 없다")
    void 자기자신은_팔로우할_수_없다() {
        when(userRepository.findByUsername("me")).thenReturn(Optional.of(me));
        when(userRepository.findById(1L)).thenReturn(Optional.of(me));

        assertThatThrownBy(() -> followService.toggleFollow("me", 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("자기 자신");

        verify(followRepository, never()).save(any(Follow.class));
    }

    @Test
    @DisplayName("정지된 유저는 팔로우할 수 없다")
    void 정지된_유저는_팔로우할_수_없다() {
        other.setSuspendedUntil(LocalDateTime.now().plusDays(1));
        when(userRepository.findByUsername("me")).thenReturn(Optional.of(me));
        when(userRepository.findById(2L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> followService.toggleFollow("me", 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("정지");

        verify(followRepository, never()).save(any(Follow.class));
    }
}

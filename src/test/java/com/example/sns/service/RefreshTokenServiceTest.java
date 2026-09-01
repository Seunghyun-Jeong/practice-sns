package com.example.sns.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.sns.entity.RefreshToken;
import com.example.sns.entity.User;
import com.example.sns.repository.RefreshTokenRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 리프레시 토큰의 검증과 회전 규칙 테스트.
 *
 * 회전은 "옛 토큰을 못 쓰게 한다"가 목적이지만, 페이지 로드처럼 요청이 동시에 여러 개
 * 나가는 상황에서 옛 토큰을 곧바로 거절하면 아무 잘못 없는 사용자가 로그아웃된다.
 * 그래서 유예 시간 안팎의 동작이 달라야 하고, 그 경계를 여기서 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private User user;

    /** 발급된 원문 */
    private String raw;

    /** 그 원문에 대응해 저장된 행 */
    private RefreshToken stored;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .username("tester")
                .password("encoded")
                .role(User.Role.USER)
                .build();
    }

    /**
     * 토큰을 한 번 발급하고, 저장되려던 엔티티를 가로채 둔다.
     * 원문은 발급 시점에만 알 수 있고 DB에는 해시만 남으므로 이렇게 짝을 맞춰둔다.
     */
    private void issueOnce() {
        raw = refreshTokenService.issue(user);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        stored = captor.getValue();
    }

    /** 저장된 모든 엔티티를 순서대로 (발급된 것, 회전된 것 순) */
    private List<RefreshToken> allSaved(int expectedCount) {
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(expectedCount)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("발급한 토큰은 원문이 아니라 해시로 저장된다")
    void 원문은_저장하지_않는다() {
        issueOnce();

        assertThat(stored.getTokenHash()).isNotEqualTo(raw);
        assertThat(stored.getTokenHash()).hasSize(64);
        assertThat(stored.getUser()).isEqualTo(user);
    }

    @Test
    @DisplayName("현역 토큰이면 주인을 찾아준다")
    void 현역_토큰의_주인을_찾는다() {
        issueOnce();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        assertThat(refreshTokenService.findOwner(raw)).contains(user);
    }

    @Test
    @DisplayName("주인만 확인할 때는 토큰을 회전시키지 않는다")
    void 조회는_회전시키지_않는다() {
        issueOnce();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        refreshTokenService.findOwner(raw);

        // 인증에 실패할 수도 있는데 미리 회전시키면, 정지가 풀렸을 때 이유 없이 로그아웃된다
        assertThat(stored.getReplacedAt()).isNull();
        allSaved(1);
    }

    @Test
    @DisplayName("회전시키면 새 토큰이 나온다")
    void 회전시키면_새_토큰이_나온다() {
        issueOnce();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));
        when(refreshTokenRepository.claimForRotation(anyString(), any())).thenReturn(1);

        Optional<String> rotated = refreshTokenService.rotate(raw);

        assertThat(rotated).isPresent();
        assertThat(rotated.get()).isNotEqualTo(raw);
    }

    @Test
    @DisplayName("동시에 들어와 회전을 놓친 요청은 새 토큰을 만들지 않는다")
    void 회전을_놓치면_토큰을_만들지_않는다() {
        issueOnce();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));
        // 다른 요청이 먼저 교체 표시를 찍은 상황
        when(refreshTokenRepository.claimForRotation(anyString(), any())).thenReturn(0);

        assertThat(refreshTokenService.rotate(raw)).isEmpty();
        // 쓰이지도 않을 토큰이 DB에 쌓이면 안 된다
        allSaved(1);
    }

    @Test
    @DisplayName("회전된 토큰의 만료 시각은 처음 것을 그대로 이어받는다")
    void 회전해도_수명은_늘지_않는다() {
        issueOnce();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));
        when(refreshTokenRepository.claimForRotation(anyString(), any())).thenReturn(1);

        refreshTokenService.rotate(raw);

        // 저장은 두 번 — 처음 발급과 회전
        RefreshToken rotated = allSaved(2).get(1);
        assertThat(rotated.getExpiresAt()).isEqualTo(stored.getExpiresAt());
        assertThat(rotated.getTokenHash()).isNotEqualTo(stored.getTokenHash());
    }

    @Test
    @DisplayName("회전 직후 유예 시간 안이면 옛 토큰으로도 주인을 찾을 수 있다")
    void 유예_안의_옛_토큰은_통과한다() {
        issueOnce();
        stored.markReplaced(LocalDateTime.now().minusSeconds(5));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        assertThat(refreshTokenService.findOwner(raw)).contains(user);
    }

    @Test
    @DisplayName("유예로 통과한 옛 토큰은 다시 회전시키지 않는다")
    void 유예_안의_옛_토큰은_재회전하지_않는다() {
        issueOnce();
        stored.markReplaced(LocalDateTime.now().minusSeconds(5));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        // 클라이언트는 이미 새 토큰을 받아갔으므로 다시 내려줄 것이 없다
        assertThat(refreshTokenService.rotate(raw)).isEmpty();
        allSaved(1);
    }

    @Test
    @DisplayName("유예 시간이 지난 옛 토큰은 거부된다")
    void 유예_지난_옛_토큰은_거부된다() {
        issueOnce();
        stored.markReplaced(LocalDateTime.now().minusMinutes(5));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        assertThat(refreshTokenService.findOwner(raw)).isEmpty();
        assertThat(refreshTokenService.rotate(raw)).isEmpty();
    }

    @Test
    @DisplayName("만료된 토큰은 회전시키지 않고 거부한다")
    void 만료된_토큰은_거부된다() {
        issueOnce();
        RefreshToken expired = RefreshToken.builder()
                .tokenHash(stored.getTokenHash())
                .user(user)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .createdAt(LocalDateTime.now().minusDays(15))
                .build();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        assertThat(refreshTokenService.findOwner(raw)).isEmpty();
        assertThat(refreshTokenService.rotate(raw)).isEmpty();
        assertThat(expired.getReplacedAt()).isNull();
    }

    @Test
    @DisplayName("모르는 토큰이면 거부한다")
    void 없는_토큰은_거부된다() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThat(refreshTokenService.findOwner("made-up-token")).isEmpty();
    }

    @Test
    @DisplayName("토큰이 없으면 DB를 조회하지도 않는다")
    void 빈_토큰은_조회하지_않는다() {
        assertThat(refreshTokenService.findOwner(null)).isEmpty();
        assertThat(refreshTokenService.findOwner("")).isEmpty();

        verify(refreshTokenRepository, never()).findByTokenHash(any());
    }

    @Test
    @DisplayName("로그아웃하면 그 로그인에서 파생된 토큰을 묶음으로 지운다")
    void 로그아웃하면_묶음을_지운다() {
        issueOnce();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        refreshTokenService.revoke(raw);

        // 제시된 값 하나만 지우면 회전으로 생긴 후속 토큰이 살아남아 계속 재발급을 받는다
        verify(refreshTokenRepository).deleteByFamilyId(stored.getFamilyId());
    }

    @Test
    @DisplayName("회전된 토큰은 같은 묶음을 물려받는다")
    void 회전해도_묶음은_같다() {
        issueOnce();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));
        when(refreshTokenRepository.claimForRotation(anyString(), any())).thenReturn(1);

        refreshTokenService.rotate(raw);

        assertThat(allSaved(2).get(1).getFamilyId()).isEqualTo(stored.getFamilyId());
    }
}

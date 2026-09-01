package com.example.sns.service;

import com.example.sns.entity.RefreshToken;
import com.example.sns.entity.User;
import com.example.sns.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리프레시 토큰 발급과 검증.
 *
 * 토큰은 JWT가 아니라 그냥 난수 문자열이다. 어차피 DB를 조회해야 하므로 서명을 검증할 이유가 없고,
 * 이 토큰의 목적 자체가 "서버가 취소할 수 있는 것"이라 자기 자신만으로 유효한 JWT와는 맞지 않는다.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    /** 리프레시 토큰 수명 */
    public static final Duration VALIDITY = Duration.ofDays(14);

    /**
     * 회전으로 교체된 직후, 옛 토큰을 잠시 더 받아주는 시간.
     *
     * 페이지 하나를 열면 요청이 동시에 여러 개 나간다. 액세스 토큰이 막 만료된 순간이라면
     * 그 요청들이 전부 같은 리프레시 토큰을 들고 갱신을 시도한다. 먼저 도착한 하나가 회전시킨 뒤
     * 나머지를 바로 거절해버리면, 아무 잘못 없는 사용자가 로그아웃된다.
     */
    private static final Duration GRACE = Duration.ofSeconds(60);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;

    /** 로그인 한 번당 한 행. 다른 기기의 로그인은 건드리지 않는다 */
    @Transactional
    public String issue(User user) {
        LocalDateTime now = LocalDateTime.now();
        String raw = generateToken();

        refreshTokenRepository.save(RefreshToken.builder()
                .tokenHash(hash(raw))
                .user(user)
                .familyId(UUID.randomUUID().toString())
                .expiresAt(now.plus(VALIDITY))
                .createdAt(now)
                .build());

        return raw;
    }

    /**
     * 이 토큰이 아직 쓸 수 있는 것인지 확인하고 주인을 돌려준다. 아무것도 바꾸지 않는다.
     *
     * 회전과 나눠 둔 이유가 있다. 인증이 될지 안 될지 모르는 상태에서 먼저 회전시켜버리면,
     * 정지된 계정처럼 인증에 실패하는 경우에도 토큰이 이미 교체된 뒤다. 그러면 정지가 풀렸을 때
     * 그 사람은 이유 없이 로그아웃되어 있다. 그래서 "확인 → 인증 → 회전" 순서로 쓴다.
     *
     * 인증이 막힌 이유를 가려내는 곳(JwtAuthEntryPoint)에서도 이쪽을 쓴다.
     * 에러 응답을 만들면서 토큰을 바꿔서는 안 되기 때문이다.
     */
    @Transactional(readOnly = true)
    public Optional<User> findOwner(String rawToken) {
        return findUsable(rawToken).map(RefreshToken::getUser);
    }

    /**
     * 인증이 끝난 뒤 토큰을 회전시킨다.
     * 유예 시간에 걸려 통과한 옛 토큰이라면 회전시키지 않고 비어 있는 값을 준다
     * (그 경우 클라이언트는 이미 새 토큰을 받아갔으므로 다시 내려줄 것이 없다).
     */
    @Transactional
    public Optional<String> rotate(String rawToken) {
        Optional<RefreshToken> found = findUsable(rawToken);
        if (found.isEmpty() || !found.get().isCurrent()) {
            return Optional.empty();
        }

        // 아래 갱신 쿼리가 영속성 컨텍스트를 비우므로 필요한 값을 먼저 꺼내 둔다
        User owner = found.get().getUser();
        LocalDateTime expiresAt = found.get().getExpiresAt();
        String familyId = found.get().getFamilyId();
        LocalDateTime now = LocalDateTime.now();

        // 여기까지 "현역"으로 읽고 온 요청이 여럿일 수 있다. 실제로 표시를 찍은 하나만 이어간다.
        if (refreshTokenRepository.claimForRotation(hash(rawToken), now) == 0) {
            return Optional.empty();
        }

        String rotated = generateToken();

        // 만료 시각은 처음 로그인할 때 정한 것을 그대로 이어받는다.
        // 회전할 때마다 수명을 늘리면 토큰이 새어나갔을 때 공격자가 무한히 연장할 수 있다.
        refreshTokenRepository.save(RefreshToken.builder()
                .tokenHash(hash(rotated))
                .user(owner)
                .familyId(familyId)     // 같은 로그인에서 이어진 것이므로 묶음을 그대로 물려받는다
                .expiresAt(expiresAt)
                .createdAt(now)
                .build());

        return Optional.of(rotated);
    }

    /** 없거나 만료됐거나 유예까지 지난 옛 토큰이면 비어 있는 값 */
    private Optional<RefreshToken> findUsable(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash(hash(rawToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }

        RefreshToken token = found.get();
        LocalDateTime now = LocalDateTime.now();

        if (token.isExpired(now)) {
            return Optional.empty();
        }

        // 이미 교체된 토큰은 유예 안에서만 받아준다
        if (!token.isCurrent() && !token.getReplacedAt().isAfter(now.minus(GRACE))) {
            return Optional.empty();
        }

        return Optional.of(token);
    }

    /**
     * 로그아웃. 이 기기의 토큰만 지우고 다른 기기는 그대로 둔다.
     *
     * 제시된 값 하나만 지우면 안 된다. 회전 때문에 같은 로그인에서 나온 토큰이 여러 개일 수 있고,
     * 그중 후속 토큰이 남으면 로그아웃했는데도 계속 재발급을 받을 수 있다.
     */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> refreshTokenRepository.deleteByFamilyId(token.getFamilyId()));
    }

    /** 만료된 행과, 교체된 뒤 유예까지 지나 더 이상 쓰이지 않는 행을 하루에 한 번 치운다 */
    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void cleanUpUnusableTokens() {
        LocalDateTime now = LocalDateTime.now();
        refreshTokenRepository.deleteUnusable(now, now.minus(GRACE));
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 원문 대신 저장할 값. 같은 입력에 같은 결과가 나와야 조회할 수 있다 */
    private String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 모든 자바 구현이 제공하므로 여기로 올 일은 없다
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }
}

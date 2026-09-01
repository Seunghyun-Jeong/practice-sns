package com.example.sns.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 로그인 한 번당 한 행. 액세스 토큰이 만료됐을 때 이 토큰으로 새 액세스 토큰을 발급한다.
 *
 * 토큰 원문은 저장하지 않고 SHA-256 해시만 둔다. DB가 새더라도 그대로 계정에
 * 접근할 수는 없게 하기 위해서다. 조회가 필요하므로 매번 값이 달라지는 BCrypt가 아니라
 * 같은 입력에 같은 결과가 나오는 해시를 쓴다 (토큰 자체가 고엔트로피 난수라 느린 해시가 필요 없다).
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    /**
     * 같은 로그인에서 파생된 토큰들의 묶음.
     *
     * 회전을 하면 토큰 값이 계속 바뀌기 때문에, 로그아웃할 때 제시된 값 하나만 지우면
     * 회전으로 생긴 후속 토큰이 살아남는다. 그것도 그 기기의 것이라 함께 사라져야 한다.
     * 묶음 단위로 지우면 다른 기기의 로그인은 그대로 두면서 이 기기만 끊을 수 있다.
     */
    @Column(nullable = false, length = 36)
    private String familyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * 회전으로 교체된 시각. null이면 아직 현역이다.
     *
     * 교체 즉시 지우지 않는 이유: 페이지 하나를 열면 요청이 동시에 여러 개 나가는데,
     * 액세스 토큰이 막 만료된 순간이면 그것들이 전부 같은 리프레시 토큰으로 갱신을 시도한다.
     * 먼저 도착한 하나가 회전시킨 뒤 나머지를 바로 거절하면 멀쩡한 사용자가 로그아웃된다.
     * 그래서 교체된 뒤에도 잠시(GRACE_SECONDS) 동안은 인증을 허용한다.
     */
    @Column
    private LocalDateTime replacedAt;

    public void markReplaced(LocalDateTime at) {
        this.replacedAt = at;
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt.isBefore(now);
    }

    /** 아직 교체되지 않았다 = 이 토큰으로 회전을 시켜도 되는 상태다 */
    public boolean isCurrent() {
        return replacedAt == null;
    }
}

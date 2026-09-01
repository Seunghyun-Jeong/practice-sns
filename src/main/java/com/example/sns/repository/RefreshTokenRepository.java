package com.example.sns.repository;

import com.example.sns.entity.RefreshToken;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** 사용자까지 같이 가져온다. 갱신할 때마다 유저를 다시 조회하지 않기 위해서다 */
    @Query("SELECT r FROM RefreshToken r JOIN FETCH r.user WHERE r.tokenHash = :hash")
    Optional<RefreshToken> findByTokenHash(@Param("hash") String hash);

    /** 한 기기의 로그인에서 파생된 토큰 전부. 로그아웃은 이 단위로 끊는다 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RefreshToken r WHERE r.familyId = :familyId")
    int deleteByFamilyId(@Param("familyId") String familyId);

    /**
     * 아직 교체되지 않은 경우에만 교체 표시를 찍는다.
     *
     * 조회와 수정을 따로 하면, 동시에 들어온 요청들이 모두 "아직 현역"으로 읽은 뒤
     * 각자 새 토큰을 만들어버린다. 로그인 한 번에 쓰이지도 않는 토큰이 여러 개 생기는 것이라
     * 판정을 DB에 맡겨 실제로 표시를 찍은 하나만 회전을 이어가게 한다.
     *
     * @return 1이면 이 요청이 회전을 가져간 것이고, 0이면 다른 요청이 이미 가져간 것이다
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.replacedAt = :now "
            + "WHERE r.tokenHash = :hash AND r.replacedAt IS NULL")
    int claimForRotation(@Param("hash") String hash, @Param("now") LocalDateTime now);

    /**
     * 청소 대상은 두 가지다.
     * 만료된 행과, 교체된 뒤 유예 시간까지 지나 더 이상 쓰이지 않는 행.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :now OR r.replacedAt < :graceLimit")
    int deleteUnusable(@Param("now") LocalDateTime now, @Param("graceLimit") LocalDateTime graceLimit);
}

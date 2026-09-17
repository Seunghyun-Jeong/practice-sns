package com.example.sns.repository;

import com.example.sns.dto.ReportGroupDto;
import com.example.sns.dto.ReporterStatsDto;
import com.example.sns.entity.Report;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /** 같은 사람이 같은 대상을 중복으로 신고하는 것을 막는다 (처리된 뒤에는 다시 신고 가능) */
    boolean existsByReporterIdAndTargetUserIdAndStatus(Long reporterId, Long targetUserId, Report.Status status);

    /**
     * 관리자 목록: 대기 중인 신고를 피신고자별로 묶는다.
     * 유저 정보까지 JOIN으로 같이 가져와 쿼리 한 번으로 끝낸다.
     * 상태를 파라미터로 받는 이유는 JPQL에 중첩 enum을 문자로 적는 표기가
     * 구현체마다 달라서 깨지기 쉽기 때문이다.
     */
    @Query("SELECT new com.example.sns.dto.ReportGroupDto(" +
           "  u.id, u.username, u.profileImageUrl, u.suspendedUntil, COUNT(r), MAX(r.createdAt)) " +
           "FROM Report r JOIN r.targetUser u " +
           "WHERE r.status = :status " +
           "GROUP BY u.id, u.username, u.profileImageUrl, u.suspendedUntil " +
           "ORDER BY COUNT(r) DESC, MAX(r.createdAt) DESC")
    List<ReportGroupDto> findGroupsByStatus(@Param("status") Report.Status status);

    /**
     * 처리 완료 목록: 처리된 신고를 피신고자별로 묶는다.
     * 한 사람에게 제재와 반려가 섞여 있을 수 있어 두 수를 나눠서 센다.
     * 정렬은 마지막 처리 시각 기준이다. 대기 목록처럼 건수 순으로 두면
     * 오래전에 많이 처리한 사람이 계속 위에 남아 최근 조치가 묻힌다.
     */
    @Query("SELECT new com.example.sns.dto.ReportGroupDto(" +
           "  u.id, u.username, u.profileImageUrl, u.suspendedUntil, COUNT(r), MAX(r.handledAt), " +
           "  SUM(CASE WHEN r.status = :resolved THEN 1L ELSE 0L END), " +
           "  SUM(CASE WHEN r.status = :rejected THEN 1L ELSE 0L END)) " +
           "FROM Report r JOIN r.targetUser u " +
           "WHERE r.status <> :pending " +
           "GROUP BY u.id, u.username, u.profileImageUrl, u.suspendedUntil " +
           "ORDER BY MAX(r.handledAt) DESC")
    List<ReportGroupDto> findHandledGroups(@Param("pending") Report.Status pending,
                                           @Param("resolved") Report.Status resolved,
                                           @Param("rejected") Report.Status rejected);

    /** 한 유저가 받은 신고 전체 (처리된 것도 포함 — 반복 신고 대상인지 보려면 이력이 필요하다) */
    @Query("SELECT r FROM Report r JOIN FETCH r.reporter " +
           "WHERE r.targetUser.id = :targetUserId ORDER BY r.createdAt DESC")
    List<Report> findByTargetWithReporter(@Param("targetUserId") Long targetUserId);

    List<Report> findByTargetUserIdAndStatus(Long targetUserId, Report.Status status);

    /**
     * 신고자들의 최근 이력을 한 번에 집계한다.
     * 신고 행마다 따로 세면 상세 화면에서 N+1이 된다.
     */
    @Query("SELECT new com.example.sns.dto.ReporterStatsDto(" +
           "  r.reporter.id, COUNT(r), " +
           "  SUM(CASE WHEN r.status = :rejected THEN 1L ELSE 0L END)) " +
           "FROM Report r " +
           "WHERE r.reporter.id IN :reporterIds AND r.createdAt >= :since " +
           "GROUP BY r.reporter.id")
    List<ReporterStatsDto> findReporterStats(@Param("reporterIds") List<Long> reporterIds,
                                             @Param("since") LocalDateTime since,
                                             @Param("rejected") Report.Status rejected);

    long countByStatus(Report.Status status);
}

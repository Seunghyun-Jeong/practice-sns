package com.example.sns.repository;

import com.example.sns.entity.NotificationOutbox;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    @Query("SELECT o FROM NotificationOutbox o WHERE o.processedAt IS NULL AND o.failedAt IS NULL ORDER BY o.id")
    List<NotificationOutbox> findPending(Limit limit);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE NotificationOutbox o SET o.processedAt = :now WHERE o.id IN :ids")
    void markProcessed(@Param("ids") List<Long> ids, @Param("now") LocalDateTime now);

    /** 실패로 확정된 줄은 원인을 보려고 남기고, 처리된 줄만 지운다 */
    @Query("SELECT o.id FROM NotificationOutbox o WHERE o.processedAt < :cutoff ORDER BY o.id")
    List<Long> findProcessedIdsBefore(@Param("cutoff") LocalDateTime cutoff, Limit limit);
}

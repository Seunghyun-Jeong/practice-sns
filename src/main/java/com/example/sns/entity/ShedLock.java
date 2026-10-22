package com.example.sns.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

/**
 * ShedLock이 쓰는 잠금 표. 코드에서 읽고 쓰지 않고, 표를 만들기 위해서만 둔다.
 *
 * 이 프로젝트는 마이그레이션 도구 없이 ddl-auto=update로 스키마를 맞추므로,
 * 엔티티로 선언해야 운영 DB와 테스트 DB에 같은 표가 생긴다.
 * 컬럼 이름과 모양은 ShedLock의 JdbcTemplateLockProvider가 기대하는 그대로다.
 */
@Entity(name = "shedlock")
public class ShedLock {
    @Id
    @Column(length = 64)
    private String name;

    @Column(nullable = false)
    private LocalDateTime lockUntil;

    @Column(nullable = false)
    private LocalDateTime lockedAt;

    @Column(nullable = false)
    private String lockedBy;
}

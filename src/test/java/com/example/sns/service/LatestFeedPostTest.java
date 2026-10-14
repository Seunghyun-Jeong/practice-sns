package com.example.sns.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.sns.dto.FeedCursor;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 전체 피드의 가장 최근 글을 기억하는 동작.
 * 실제 시계를 기다리지 않도록 시간을 직접 넘긴다.
 */
class LatestFeedPostTest {

    private final MovableClock clock = new MovableClock();
    private final AtomicInteger loads = new AtomicInteger();
    private LatestFeedPost latest;

    @BeforeEach
    void setUp() {
        latest = new LatestFeedPost(Duration.ofSeconds(5), clock);
    }

    @Test
    @DisplayName("기억하는 시간 안에는 다시 읽지 않는다")
    void 기억하는_동안은_다시_읽지_않는다() {
        latest.get(this::load);
        clock.advance(Duration.ofSeconds(4));
        latest.get(this::load);

        assertThat(loads.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("기억하는 시간이 지나면 다시 읽는다")
    void 시간이_지나면_다시_읽는다() {
        latest.get(this::load);
        clock.advance(Duration.ofSeconds(5));
        latest.get(this::load);

        assertThat(loads.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("새 글이 올라와 지우면 시간이 남아 있어도 다시 읽는다")
    void 지우면_바로_다시_읽는다() {
        latest.get(this::load);
        latest.evict();
        latest.get(this::load);

        assertThat(loads.get()).isEqualTo(2);
    }

    private FeedCursor load() {
        return new FeedCursor(LocalDateTime.now(), (long) loads.incrementAndGet());
    }

    /** 테스트에서 시간을 앞으로 옮길 수 있는 시계 */
    private static class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}

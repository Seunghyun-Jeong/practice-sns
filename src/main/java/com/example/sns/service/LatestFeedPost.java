package com.example.sns.service;

import com.example.sns.dto.FeedCursor;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 전체 피드의 가장 최근 글 자리를 잠깐 기억해 둔다.
 *
 * 새 글 확인은 피드를 보는 사람 모두가 주기적으로 묻는데, 전체 피드의 맨 위 글은 누구에게나 같다.
 * 그래서 기억해 둔 동안의 질문은 DB에 가지 않고 이 값과 비교만 한다. 접속자가 늘어도 DB 조회는
 * 기억하는 시간마다 한 번이다.
 *
 * 새 글이 올라오면 바로 지운다. 기억하는 시간은 지우지 않는 변화(글 삭제, 사용자 정지)가 반영되기까지
 * 걸리는 최대 시간이기도 하다.
 */
@Component
public class LatestFeedPost {

    private final Duration ttl;
    private final Clock clock;
    private volatile Snapshot snapshot;

    /** latest는 글이 하나도 없으면 null */
    private record Snapshot(FeedCursor latest, Instant loadedAt) {
    }

    @Autowired
    public LatestFeedPost(@Value("${feed.latest-cache-ttl:5s}") Duration ttl) {
        this(ttl, Clock.systemUTC());
    }

    LatestFeedPost(Duration ttl, Clock clock) {
        this.ttl = ttl;
        this.clock = clock;
    }

    /** 기억한 값이 없거나 기억하는 시간이 지났으면 loader로 다시 읽는다 */
    public FeedCursor get(Supplier<FeedCursor> loader) {
        Snapshot current = snapshot;
        Instant now = clock.instant();
        if (current == null || !now.isBefore(current.loadedAt().plus(ttl))) {
            current = new Snapshot(loader.get(), now);
            snapshot = current;
        }
        return current.latest();
    }

    /** 새 글이 올라왔을 때 부른다. 다음 질문 때 새로 읽는다 */
    public void evict() {
        snapshot = null;
    }
}

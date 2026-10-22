package com.example.sns.event;

/**
 * 알림 이벤트 토픽 이름. 보내는 쪽(NotificationOutboxRelay)과 받는 쪽(NotificationEventConsumer)이 같이 쓴다.
 *
 * 토픽은 앱이 만들지 않는다. 파티션 수와 복제 수는 운영 결정이라 인프라가 정하고,
 * 로컬에서는 docker-compose.yml이 만든다. 앱이 만들게 했더니 Kafka보다 앱이 먼저 뜬 경우 생성을 놓쳤고,
 * 브로커가 기본값(파티션 1개)으로 대신 만들었다. 그래서 브로커 자동 생성도 끈다.
 *
 * 이벤트 종류(좋아요, 댓글, 팔로우)별로 토픽을 나누지 않는다. Kafka는 한 파티션 안에서만 순서를 지키는데,
 * 토픽이 다르면 같은 사람에게 가는 좋아요와 좋아요 취소가 따로 흘러 취소가 먼저 반영될 수 있다.
 * 토픽 하나에 받는 사람 id를 키로 보내면 그 사람의 이벤트는 늘 같은 파티션에 들어가 순서가 지켜진다.
 */
public final class NotificationTopic {

    public static final String NAME = "notification-events";

    private NotificationTopic() {
    }
}

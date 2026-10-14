package com.example.sns.service;

import com.example.sns.config.PushSocketHandler;
import com.example.sns.repository.ChatMessageRepository;
import com.example.sns.repository.NotificationRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 헤더 배지 숫자(안 읽은 알림, 안 읽은 DM)의 주인.
 *
 * 숫자를 세는 곳과 접속자에게 밀어주는 곳을 여기 한 곳에 둔다. 헤더를 그릴 때, 알림이 생기거나 지워질 때,
 * DM이 오가거나 읽힐 때, 재연결 후 다시 받을 때 모두 이것만 부른다.
 * 지금은 인덱스로 바로 세지만, 사용자가 많아져 카운터를 따로 저장(Redis 등)하거나 푸시를 브로커로 보내게 되면
 * 이 클래스 안쪽만 바꾸면 되도록 부르는 쪽은 숫자를 어떻게 구하는지 모르게 둔다.
 */
@Service
@RequiredArgsConstructor
public class BadgeService {

    private final NotificationRepository notificationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final PushSocketHandler pushSocketHandler;

    /** 안 읽은 알림 수 */
    public long unreadNotifications(Long userId) {
        return notificationRepository.countByRecipient_IdAndIsReadFalse(userId);
    }

    /** 상대가 보냈고 내가 아직 안 읽은 DM 수 */
    public long unreadChats(Long userId) {
        return chatMessageRepository.countTotalUnread(userId);
    }

    /** 접속 중이면 알림 배지 숫자를 밀어준다. 접속하지 않았으면 세지도 않는다 */
    public void pushNotificationBadge(Long userId) {
        pushSocketHandler.pushToUser(userId, () -> Map.of("type", "noti-badge", "count", unreadNotifications(userId)));
    }

    /** 접속 중이면 DM 배지 숫자를 밀어준다. 접속하지 않았으면 세지도 않는다 */
    public void pushChatBadge(Long userId) {
        pushSocketHandler.pushToUser(userId, () -> Map.of("type", "chat-badge", "count", unreadChats(userId)));
    }
}

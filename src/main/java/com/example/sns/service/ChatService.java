package com.example.sns.service;

import com.example.sns.config.PushSocketHandler;
import com.example.sns.dto.ChatMessageDto;
import com.example.sns.dto.ChatMessagePageDto;
import com.example.sns.dto.ChatRoomDto;
import com.example.sns.entity.ChatMessage;
import com.example.sns.entity.ChatRoom;
import com.example.sns.entity.User;
import com.example.sns.repository.ChatMessageRepository;
import com.example.sns.repository.ChatRoomRepository;
import com.example.sns.repository.UserRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final PushSocketHandler pushSocketHandler;

    /** 메시지 최대 길이 (컬럼 길이와 맞춘다) */
    private static final int MAX_CONTENT_LENGTH = 1000;

    /**
     * 상대와의 방을 연다. 이미 있으면 그 방을, 없으면 새로 만들어 돌려준다.
     * 어느 쪽이 먼저 열어도 같은 방이 되도록 id가 작은 쪽을 user1로 저장한다.
     */
    @Transactional
    public Long openRoom(Long myId, Long partnerId) {
        if (myId.equals(partnerId)) {
            throw new IllegalArgumentException("자기 자신과는 대화할 수 없습니다.");
        }
        User me = findUser(myId);
        User partner = findUser(partnerId);
        if (partner.isSuspended()) {
            throw new IllegalArgumentException("정지된 유저와는 대화할 수 없습니다.");
        }

        User user1 = me.getId() < partner.getId() ? me : partner;
        User user2 = me.getId() < partner.getId() ? partner : me;

        return chatRoomRepository.findByUser1AndUser2(user1, user2)
                .orElseGet(() -> {
                    ChatRoom room = new ChatRoom();
                    room.setUser1(user1);
                    room.setUser2(user2);
                    return chatRoomRepository.save(room);
                })
                .getId();
    }

    /** 내 채팅방 목록 (최근 대화 순, 마지막 메시지와 안읽은 수 포함) */
    public List<ChatRoomDto> getRooms(Long myId) {
        User me = findUser(myId);
        List<ChatRoom> rooms = chatRoomRepository.findAllByParticipant(me);
        if (rooms.isEmpty()) {
            return List.of();
        }

        // 방마다 따로 조회하면 N+1이 되므로 마지막 메시지와 안읽은 수를 각각 한 번에 가져온다
        List<Long> roomIds = rooms.stream().map(ChatRoom::getId).collect(Collectors.toList());

        Map<Long, ChatMessage> lastMessages = new HashMap<>();
        for (ChatMessage m : chatMessageRepository.findLastMessages(roomIds)) {
            lastMessages.put(m.getRoom().getId(), m);
        }

        Map<Long, Long> unreadCounts = new HashMap<>();
        for (Object[] row : chatMessageRepository.countUnreadByRoomIds(roomIds, me)) {
            unreadCounts.put((Long) row[0], (Long) row[1]);
        }

        return rooms.stream()
                .map(room -> {
                    User partner = room.getPartnerOf(myId);
                    ChatMessage last = lastMessages.get(room.getId());
                    return new ChatRoomDto(
                            room.getId(),
                            partner.getId(),
                            partner.getUsername(),
                            partner.getProfileImageUrl(),
                            last != null ? last.getContent() : null,
                            last != null && last.isDeleted(),
                            room.getLastMessageAt().toString(),
                            unreadCounts.getOrDefault(room.getId(), 0L)
                    );
                })
                .collect(Collectors.toList());
    }

    /** 방 페이지 상단에 표시할 정보 (상대가 누구인지). 참여자만 볼 수 있다 */
    public ChatRoomDto getRoom(Long myId, Long roomId) {
        ChatRoom room = findRoomOf(myId, roomId);
        User partner = room.getPartnerOf(myId);
        return new ChatRoomDto(
                room.getId(),
                partner.getId(),
                partner.getUsername(),
                partner.getProfileImageUrl(),
                null,
                false,
                room.getLastMessageAt().toString(),
                0
        );
    }

    /** 방의 메시지 한 페이지 (최신순). 참여자만 볼 수 있다 */
    public ChatMessagePageDto getMessages(Long myId, Long roomId, int page, int size) {
        ChatRoom room = findRoomOf(myId, roomId);

        Slice<ChatMessage> slice =
                chatMessageRepository.findByRoomOrderByCreatedAtDescIdDesc(room, PageRequest.of(page, size));

        List<ChatMessageDto> messages = slice.getContent().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return new ChatMessagePageDto(messages, page, slice.hasNext());
    }

    /** 메시지 전송. 참여자만 보낼 수 있고, 정지된 상대에게는 보낼 수 없다 */
    @Transactional
    public ChatMessageDto sendMessage(Long myId, Long roomId, String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("메시지 내용을 입력해주세요.");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("메시지는 " + MAX_CONTENT_LENGTH + "자 이하여야 합니다.");
        }

        ChatRoom room = findRoomOf(myId, roomId);
        if (room.getPartnerOf(myId).isSuspended()) {
            throw new IllegalArgumentException("정지된 유저와는 대화할 수 없습니다.");
        }

        ChatMessage message = new ChatMessage();
        message.setRoom(room);
        message.setSender(findUser(myId));
        message.setContent(content.trim());
        chatMessageRepository.save(message);

        room.setLastMessageAt(message.getCreatedAt());

        // 상대가 접속 중이면 새 메시지와 갱신된 안읽음 수를 바로 밀어준다
        ChatMessageDto dto = toDto(message);
        User partner = room.getPartnerOf(myId);
        pushSocketHandler.pushToUser(partner.getId(), Map.of("type", "chat-message", "message", dto));
        pushSocketHandler.pushToUser(partner.getId(),
                Map.of("type", "chat-badge", "count", chatMessageRepository.countTotalUnread(partner)));

        return dto;
    }

    /**
     * 메시지 수정. 상대가 아직 읽지 않은 내 메시지만 가능하다.
     * 수정 후에는 상대 화면도 갱신되도록 푸시한다.
     */
    @Transactional
    public ChatMessageDto editMessage(Long myId, Long messageId, String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("메시지 내용을 입력해주세요.");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("메시지는 " + MAX_CONTENT_LENGTH + "자 이하여야 합니다.");
        }

        ChatMessage message = findModifiableMessage(myId, messageId);
        message.setContent(content.trim());
        message.setEdited(true);

        ChatMessageDto dto = toDto(message);
        pushToPartner(message, myId, dto);
        return dto;
    }

    /**
     * 메시지 삭제. 상대가 아직 읽지 않은 내 메시지만 가능하다.
     * 행은 남기고 표시만 바꾼다 — 그 자리에 "삭제된 메시지입니다"를 그려야 하기 때문이다.
     */
    @Transactional
    public ChatMessageDto deleteMessage(Long myId, Long messageId) {
        ChatMessage message = findModifiableMessage(myId, messageId);
        message.setContent("");   // 안 읽힌 내용이라 보관하지 않는다
        message.setDeleted(true);

        ChatMessageDto dto = toDto(message);
        pushToPartner(message, myId, dto);
        return dto;
    }

    /**
     * 수정·삭제가 가능한 메시지인지 확인한다.
     * 두 기능이 같은 규칙을 쓰도록 한곳에 모았다.
     * 클라이언트가 메뉴를 숨기더라도, 메뉴가 떠 있는 사이 상대가 읽을 수 있으므로
     * 서버에서 읽음 여부를 최종 판단한다.
     */
    private ChatMessage findModifiableMessage(Long myId, Long messageId) {
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("메시지를 찾을 수 없습니다."));

        if (!message.getSender().getId().equals(myId)) {
            throw new IllegalArgumentException("본인이 보낸 메시지만 수정하거나 삭제할 수 있습니다.");
        }
        if (message.isDeleted()) {
            throw new IllegalArgumentException("이미 삭제된 메시지입니다.");
        }
        if (message.isRead()) {
            throw new IllegalArgumentException("상대가 읽은 메시지는 수정하거나 삭제할 수 없습니다.");
        }
        return message;
    }

    /** 수정·삭제 결과를 상대 화면에도 반영시킨다 */
    private void pushToPartner(ChatMessage message, Long myId, ChatMessageDto dto) {
        Long partnerId = message.getRoom().getPartnerOf(myId).getId();
        pushSocketHandler.pushToUser(partnerId, Map.of("type", "chat-message-updated", "message", dto));
    }

    /** 방에 들어왔을 때 상대가 보낸 메시지를 읽음 처리 */
    @Transactional
    public void markAsRead(Long myId, Long roomId) {
        ChatRoom room = findRoomOf(myId, roomId);
        User me = findUser(myId);
        int updated = chatMessageRepository.markAllAsRead(room, me);
        // 다른 탭에 떠 있는 내 배지도 함께 줄어들도록 밀어준다
        pushSocketHandler.pushToUser(myId,
                Map.of("type", "chat-badge", "count", chatMessageRepository.countTotalUnread(me)));
        // 실제로 읽은 게 있으면, 보낸 사람 화면의 읽음 표시가 바로 켜지도록 알린다
        if (updated > 0) {
            pushSocketHandler.pushToUser(room.getPartnerOf(myId).getId(),
                    Map.of("type", "chat-read", "roomId", room.getId()));
        }
    }

    /** 헤더 배지용: 내가 안 읽은 메시지 전체 수 */
    public long getUnreadCount(Long myId) {
        return chatMessageRepository.countTotalUnread(findUser(myId));
    }

    /** 방을 찾고, 내가 참여자가 아니면 거부한다 */
    private ChatRoom findRoomOf(Long myId, Long roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));
        if (!room.hasParticipant(myId)) {
            throw new IllegalArgumentException("참여 중인 채팅방이 아닙니다.");
        }
        return room;
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));
    }

    private ChatMessageDto toDto(ChatMessage m) {
        return new ChatMessageDto(
                m.getId(),
                m.getRoom().getId(),
                m.getSender().getId(),
                m.getSender().getUsername(),
                m.getContent(),
                m.getCreatedAt().toString(),
                m.isRead(),
                m.isEdited(),
                m.isDeleted()
        );
    }
}

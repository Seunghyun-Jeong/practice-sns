package com.example.sns.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.sns.dto.ChatMessageDto;
import com.example.sns.dto.ChatMessagePageDto;
import com.example.sns.entity.ChatMessage;
import com.example.sns.entity.ChatRoom;
import com.example.sns.entity.User;
import com.example.sns.repository.ChatMessageRepository;
import com.example.sns.repository.ChatRoomRepository;
import com.example.sns.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대화방 "이전 메시지 보기" 테스트.
 *
 * 채팅은 예전 대화를 보러 올라가는 사이에도 메시지가 계속 오간다. 페이지 번호로 읽으면 그만큼 밀려
 * 이미 보이는 메시지가 다시 붙는다. 이전 묶음은 화면 맨 위 메시지의 (보낸 시각, id)보다 먼저 보낸 것이라
 * 메시지가 오가도, 보낸 시각이 같은 메시지가 묶음 경계에 걸려도 겹치거나 빠지면 안 된다.
 */
@SpringBootTest
@Transactional
class ChatMessagePageTest {

    private static final int PAGE_SIZE = 30;

    @Autowired
    private ChatService chatService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private EntityManager entityManager;

    private User alice;
    private User bob;
    private Long roomId;

    @BeforeEach
    void setUp() {
        alice = createUser("alice");
        bob = createUser("bob");
        roomId = chatService.openRoom(alice.getId(), bob.getId());
    }

    @Test
    @DisplayName("방을 연 뒤 메시지가 오가도 이전 메시지 묶음에 이미 보이는 메시지가 다시 붙지 않는다")
    void 메시지가_오가도_밀리지_않는다() {
        for (int i = 0; i < 40; i++) {
            chatService.sendMessage(i % 2 == 0 ? alice.getId() : bob.getId(), roomId, "m" + i);
        }

        ChatMessagePageDto latest = chatService.getMessages(alice.getId(), roomId, null, null, PAGE_SIZE);
        for (int i = 0; i < 3; i++) {
            chatService.sendMessage(bob.getId(), roomId, "new" + i);
        }
        ChatMessageDto top = oldestOf(latest);
        ChatMessagePageDto previous = chatService.getMessages(alice.getId(), roomId,
                LocalDateTime.parse(top.getCreatedAt()), top.getId(), PAGE_SIZE);

        assertThat(top.getContent()).isEqualTo("m10");
        assertThat(previous.getMessages()).extracting(ChatMessageDto::getContent).startsWith("m9").endsWith("m0");
        assertThat(ids(previous)).doesNotContainAnyElementsOf(ids(latest));
    }

    @Test
    @DisplayName("보낸 시각이 같은 메시지가 묶음 경계에 걸려도 겹치거나 빠지지 않는다")
    void 시각이_같아도_겹치지도_빠지지도_않는다() {
        ChatRoom room = chatRoomRepository.findById(roomId).orElseThrow();
        LocalDateTime sameTime = LocalDateTime.now();
        List<Long> sent = new ArrayList<>();
        for (int i = 0; i < 70; i++) {
            ChatMessage message = new ChatMessage();
            message.setRoom(room);
            message.setSender(i % 2 == 0 ? alice : bob);
            message.setContent("m" + i);
            message.setCreatedAt(sameTime);
            sent.add(chatMessageRepository.save(message).getId());
        }
        entityManager.flush();
        entityManager.clear();

        List<Long> received = new ArrayList<>();
        ChatMessagePageDto page = chatService.getMessages(alice.getId(), roomId, null, null, PAGE_SIZE);
        received.addAll(ids(page));
        while (page.isHasNext()) {
            ChatMessageDto top = oldestOf(page);
            page = chatService.getMessages(alice.getId(), roomId,
                    LocalDateTime.parse(top.getCreatedAt()), top.getId(), PAGE_SIZE);
            received.addAll(ids(page));
        }

        assertThat(received).containsExactlyInAnyOrderElementsOf(sent).doesNotHaveDuplicates();
    }

    /** 묶음은 최신순이라 마지막이 가장 오래된 메시지(화면 맨 위) */
    private ChatMessageDto oldestOf(ChatMessagePageDto page) {
        return page.getMessages().get(page.getMessages().size() - 1);
    }

    private List<Long> ids(ChatMessagePageDto page) {
        return page.getMessages().stream().map(ChatMessageDto::getId).toList();
    }

    private User createUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("password");
        user.setRole(User.Role.USER);
        return userRepository.save(user);
    }
}

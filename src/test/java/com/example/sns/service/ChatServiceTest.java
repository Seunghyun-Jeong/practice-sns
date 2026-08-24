package com.example.sns.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.sns.config.ChatSocketHandler;
import com.example.sns.entity.ChatMessage;
import com.example.sns.entity.ChatRoom;
import com.example.sns.entity.User;
import com.example.sns.repository.ChatMessageRepository;
import com.example.sns.repository.ChatRoomRepository;
import com.example.sns.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChatSocketHandler chatSocketHandler;

    @InjectMocks
    private ChatService chatService;

    private User me;
    private User other;

    @BeforeEach
    void setUp() {
        me = new User();
        me.setId(1L);
        me.setUsername("me");

        other = new User();
        other.setId(2L);
        other.setUsername("other");
    }

    @Test
    @DisplayName("상대와 방이 없으면 새로 만든다")
    void 방을_새로_만든다() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(me));
        when(userRepository.findById(2L)).thenReturn(Optional.of(other));
        when(chatRoomRepository.findByUser1AndUser2(me, other)).thenReturn(Optional.empty());
        when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(inv -> {
            ChatRoom r = inv.getArgument(0);
            r.setId(10L);
            return r;
        });

        Long roomId = chatService.openRoom(1L, 2L);

        assertThat(roomId).isEqualTo(10L);
        verify(chatRoomRepository).save(any(ChatRoom.class));
    }

    @Test
    @DisplayName("어느 쪽이 열어도 같은 방을 쓴다 (id가 작은 쪽이 user1)")
    void 반대쪽에서_열어도_같은_방() {
        ChatRoom existing = new ChatRoom();
        existing.setId(10L);
        existing.setUser1(me);
        existing.setUser2(other);

        when(userRepository.findById(1L)).thenReturn(Optional.of(me));
        when(userRepository.findById(2L)).thenReturn(Optional.of(other));
        // 상대(2L) 쪽에서 열어도 조회 순서는 (me, other)로 정렬되어야 한다
        when(chatRoomRepository.findByUser1AndUser2(me, other)).thenReturn(Optional.of(existing));

        Long roomId = chatService.openRoom(2L, 1L);

        assertThat(roomId).isEqualTo(10L);
        verify(chatRoomRepository, never()).save(any(ChatRoom.class));
    }

    @Test
    @DisplayName("자기 자신과는 방을 만들 수 없다")
    void 자기자신과는_대화할_수_없다() {
        assertThatThrownBy(() -> chatService.openRoom(1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("자기 자신");
    }

    @Test
    @DisplayName("정지된 유저와는 방을 만들 수 없다")
    void 정지된_유저와는_대화할_수_없다() {
        other.setSuspendedUntil(LocalDateTime.now().plusDays(1));
        when(userRepository.findById(1L)).thenReturn(Optional.of(me));
        when(userRepository.findById(2L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> chatService.openRoom(1L, 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("정지");
    }

    @Test
    @DisplayName("참여자가 아니면 메시지를 보낼 수 없다")
    void 남의_방에는_보낼_수_없다() {
        ChatRoom room = new ChatRoom();
        room.setId(10L);
        room.setUser1(me);
        room.setUser2(other);
        when(chatRoomRepository.findById(10L)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> chatService.sendMessage(99L, 10L, "안녕"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("참여 중인 채팅방이 아닙니다");

        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    @DisplayName("빈 메시지는 보낼 수 없다")
    void 빈_메시지는_보낼_수_없다() {
        assertThatThrownBy(() -> chatService.sendMessage(1L, 10L, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("내용");

        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    @DisplayName("읽음 처리를 하면 보낸 사람에게 읽음 이벤트를 밀어준다")
    void 읽으면_보낸_사람에게_알린다() {
        ChatRoom room = new ChatRoom();
        room.setId(10L);
        room.setUser1(me);
        room.setUser2(other);
        when(chatRoomRepository.findById(10L)).thenReturn(Optional.of(room));
        when(userRepository.findById(2L)).thenReturn(Optional.of(other));
        when(chatMessageRepository.markAllAsRead(room, other)).thenReturn(2);

        chatService.markAsRead(2L, 10L);   // other(2L)가 읽는다

        verify(chatSocketHandler).pushToUser(org.mockito.ArgumentMatchers.eq(1L), any());   // me(1L)에게 알림
    }

    @Test
    @DisplayName("읽을 메시지가 없으면 읽음 이벤트를 보내지 않는다")
    void 읽은게_없으면_알리지_않는다() {
        ChatRoom room = new ChatRoom();
        room.setId(10L);
        room.setUser1(me);
        room.setUser2(other);
        when(chatRoomRepository.findById(10L)).thenReturn(Optional.of(room));
        when(userRepository.findById(2L)).thenReturn(Optional.of(other));
        when(chatMessageRepository.markAllAsRead(room, other)).thenReturn(0);

        chatService.markAsRead(2L, 10L);

        verify(chatSocketHandler, never()).pushToUser(org.mockito.ArgumentMatchers.eq(1L), any());
    }

    @Test
    @DisplayName("메시지를 보내면 방의 최근 대화 시각이 갱신된다")
    void 보내면_방의_시각이_갱신된다() {
        ChatRoom room = new ChatRoom();
        room.setId(10L);
        room.setUser1(me);
        room.setUser2(other);
        LocalDateTime before = LocalDateTime.now().minusDays(1);
        room.setLastMessageAt(before);

        when(chatRoomRepository.findById(10L)).thenReturn(Optional.of(room));
        when(userRepository.findById(1L)).thenReturn(Optional.of(me));

        chatService.sendMessage(1L, 10L, "안녕");

        verify(chatMessageRepository).save(any(ChatMessage.class));
        assertThat(room.getLastMessageAt()).isAfter(before);
    }
}

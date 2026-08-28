package com.example.sns.controller;

import com.example.sns.config.MyUserDetails;
import com.example.sns.dto.ChatSendRequest;
import com.example.sns.service.ChatService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chats")
public class ChatController {
    private final ChatService chatService;

    /** 방 한 페이지에 불러올 메시지 수 */
    private static final int MESSAGE_PAGE_SIZE = 30;

    /** 내 채팅방 목록 */
    @GetMapping
    public ResponseEntity<?> getRooms(@AuthenticationPrincipal MyUserDetails user) {
        return ResponseEntity.ok(Map.of("rooms", chatService.getRooms(user.getUserId())));
    }

    /** 상대와의 방 열기 (없으면 생성) */
    @PostMapping("/with/{userId}")
    public ResponseEntity<?> openRoom(@PathVariable Long userId,
                                      @AuthenticationPrincipal MyUserDetails user) {
        try {
            Long roomId = chatService.openRoom(user.getUserId(), userId);
            return ResponseEntity.ok(Map.of("roomId", roomId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** 방의 메시지 목록 (최신순 페이징) */
    @GetMapping("/{roomId}/messages")
    public ResponseEntity<?> getMessages(@PathVariable Long roomId,
                                         @RequestParam(defaultValue = "0") int page,
                                         @AuthenticationPrincipal MyUserDetails user) {
        try {
            return ResponseEntity.ok(chatService.getMessages(user.getUserId(), roomId, page, MESSAGE_PAGE_SIZE));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** 메시지 전송 */
    @PostMapping("/{roomId}/messages")
    public ResponseEntity<?> sendMessage(@PathVariable Long roomId,
                                         @RequestBody ChatSendRequest request,
                                         @AuthenticationPrincipal MyUserDetails user) {
        try {
            return ResponseEntity.ok(chatService.sendMessage(user.getUserId(), roomId, request.getContent()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** 메시지 수정 (상대가 읽기 전에만) */
    @PutMapping("/messages/{messageId}")
    public ResponseEntity<?> editMessage(@PathVariable Long messageId,
                                         @RequestBody ChatSendRequest request,
                                         @AuthenticationPrincipal MyUserDetails user) {
        try {
            return ResponseEntity.ok(chatService.editMessage(user.getUserId(), messageId, request.getContent()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** 메시지 삭제 (상대가 읽기 전에만) */
    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<?> deleteMessage(@PathVariable Long messageId,
                                           @AuthenticationPrincipal MyUserDetails user) {
        try {
            return ResponseEntity.ok(chatService.deleteMessage(user.getUserId(), messageId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** 방에 들어왔을 때 읽음 처리 */
    @PostMapping("/{roomId}/read")
    public ResponseEntity<?> markAsRead(@PathVariable Long roomId,
                                        @AuthenticationPrincipal MyUserDetails user) {
        try {
            chatService.markAsRead(user.getUserId(), roomId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** 헤더 배지용: 안 읽은 메시지 전체 수 */
    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(@AuthenticationPrincipal MyUserDetails user) {
        return ResponseEntity.ok(Map.of("count", chatService.getUnreadCount(user.getUserId())));
    }
}

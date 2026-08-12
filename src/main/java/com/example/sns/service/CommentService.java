package com.example.sns.service;

import com.example.sns.dto.AdminCommentDto;
import com.example.sns.dto.CommentDto;
import com.example.sns.dto.CommentUpdateRequest;
import com.example.sns.entity.Comment;
import com.example.sns.entity.Notification;
import com.example.sns.entity.Post;
import com.example.sns.entity.User;
import com.example.sns.repository.CommentRepository;
import com.example.sns.repository.PostRepository;
import com.example.sns.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommentService {
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public void addComment(Long postId, CommentDto dto, String username) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("해당 게시글이 존재하지 않습니다."));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저가 존재하지 않습니다."));

        Comment comment = new Comment();
        comment.setPost(post);
        comment.setAuthor(user);
        comment.setContent(dto.getContent());
        comment.setCreatedAt(LocalDateTime.now());

        commentRepository.save(comment);

        // 게시글 작성자에게 알림 (어떤 댓글인지도 함께 남긴다)
        notificationService.notify(post.getAuthor(), user, Notification.Type.COMMENT, post, comment);
    }

    public void updateComment(Long commentId, CommentUpdateRequest request, String username) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글이 존재하지 않습니다."));

        if (!comment.getAuthor().getUsername().equals(username)) {
            throw new AccessDeniedException("본인이 작성한 댓글만 수정할 수 있습니다.");
        }

        comment.setContent(request.getContent());
        comment.setUpdatedAt(LocalDateTime.now());
        commentRepository.save(comment);
    }

    public List<AdminCommentDto> getCommentsByUser(Long userId) {
        return commentRepository.findByAuthor_IdOrderByCreatedAtDesc(userId).stream()
                .map(comment -> new AdminCommentDto(
                        comment.getId(),
                        comment.getContent(),
                        comment.getCreatedAt().toString(),
                        comment.getPost().getId(),
                        comment.getPost().getContent(),
                        comment.getPost().getAuthor().getUsername()
                ))
                .collect(Collectors.toList());
    }

    public void deleteComment(Long commentId, String username, String role) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 삭제할 수 없습니다."));

        if (!comment.getAuthor().getUsername().equals(username) && !"ADMIN".equals(role)) {
            throw new SecurityException("댓글 삭제 권한이 없습니다.");
        }

        commentRepository.delete(comment);
    }
}

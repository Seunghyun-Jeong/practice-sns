package com.example.sns.service;

import com.example.sns.entity.Comment;
import com.example.sns.entity.CommentLike;
import com.example.sns.entity.User;
import com.example.sns.repository.CommentLikeRepository;
import com.example.sns.repository.CommentRepository;
import com.example.sns.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommentLikeService {
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final CommentLikeRepository commentLikeRepository;

    public boolean toggleLike(Long commentId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("유저를 찾을 수 없습니다."));

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("댓글을 찾을 수 없습니다."));

        Optional<CommentLike> existingLike = commentLikeRepository.findByCommentAndUser(comment, user);

        if (existingLike.isPresent()) {
            commentLikeRepository.delete(existingLike.get());
            return false;
        } else {
            CommentLike like = new CommentLike();
            like.setComment(comment);
            like.setUser(user);
            commentLikeRepository.save(like);
            return true;
        }
    }

    public long countLikes(Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("댓글을 찾을 수 없습니다"));
        return commentLikeRepository.countByComment(comment);
    }

    public boolean hasUserLikedComment(Long commentId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("유저를 찾을 수 없습니다."));
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("댓글을 찾을 수 없습니다"));
        return commentLikeRepository.existsByCommentAndUser(comment, user);
    }
}

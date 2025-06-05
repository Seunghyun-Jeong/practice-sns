package com.example.sns.repository;

import com.example.sns.entity.Comment;
import com.example.sns.entity.CommentLike;
import com.example.sns.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {
    Optional<CommentLike> findByCommentAndUser(Comment comment, User user);
    boolean existsByCommentAndUser(Comment comment, User user);
    long countByComment(Comment comment);
}

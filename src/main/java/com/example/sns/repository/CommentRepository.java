package com.example.sns.repository;

import com.example.sns.entity.Comment;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByPostIdOrderByCreatedAtAsc(Long postId);
    List<Comment> findByAuthor_IdOrderByCreatedAtDesc(Long userId);

    /** 여러 게시글의 댓글 수를 한 번에 조회 → [postId, count] */
    @Query("SELECT c.post.id, COUNT(c) FROM Comment c "
            + "WHERE c.post.id IN :postIds GROUP BY c.post.id")
    List<Object[]> countByPostIds(@Param("postIds") Collection<Long> postIds);
}

package com.example.sns.repository;

import com.example.sns.entity.Comment;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    /**
     * 게시글 상세에 보여줄 댓글 전체 (답글 포함).
     * 작성자를 댓글마다 따로 읽지 않도록 함께 가져오고, 같은 시각에 쓴 댓글은 id 순으로 둔다.
     */
    @Query("SELECT c FROM Comment c JOIN FETCH c.author "
            + "WHERE c.post.id = :postId ORDER BY c.createdAt ASC, c.id ASC")
    List<Comment> findByPostIdWithAuthor(@Param("postId") Long postId);

    /** 이 댓글에 달린 답글 (원 댓글을 지울 때 함께 지운다) */
    List<Comment> findByParentId(Long parentId);
    List<Comment> findByAuthor_IdOrderByCreatedAtDesc(Long userId);

    /** 여러 게시글의 댓글 수를 한 번에 조회 → [postId, count] */
    @Query("SELECT c.post.id, COUNT(c) FROM Comment c "
            + "WHERE c.post.id IN :postIds GROUP BY c.post.id")
    List<Object[]> countByPostIds(@Param("postIds") Collection<Long> postIds);
}

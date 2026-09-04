package com.example.sns.repository;

import com.example.sns.entity.Comment;
import com.example.sns.entity.CommentLike;
import com.example.sns.entity.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {
    Optional<CommentLike> findByCommentAndUser(Comment comment, User user);
    boolean existsByCommentAndUser(Comment comment, User user);
    long countByComment(Comment comment);

    /**
     * 댓글들의 좋아요 수를 한 번에 센다.
     * 댓글마다 세면 댓글 수만큼 쿼리가 나가는데, 답글이 붙으면서 그 수가 더 늘었다.
     */
    @Query("SELECT cl.comment.id, COUNT(cl) FROM CommentLike cl "
            + "WHERE cl.comment.id IN :commentIds GROUP BY cl.comment.id")
    List<Object[]> countByCommentIds(@Param("commentIds") Collection<Long> commentIds);

    /** 이 사람이 좋아요를 누른 댓글의 id만 추린다 */
    @Query("SELECT cl.comment.id FROM CommentLike cl "
            + "WHERE cl.user.id = :userId AND cl.comment.id IN :commentIds")
    List<Long> findLikedCommentIds(@Param("userId") Long userId,
                                   @Param("commentIds") Collection<Long> commentIds);
}

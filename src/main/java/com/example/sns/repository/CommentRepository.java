package com.example.sns.repository;

import com.example.sns.dto.AdminCommentDto;
import com.example.sns.entity.Comment;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    /*
     * 게시글 상세의 댓글은 묶음 단위로 끊어 읽는다.
     * 작성자는 댓글마다 따로 읽지 않도록 함께 가져오고(fetch join), 다음 묶음은 페이지 번호가 아니라
     * 이미 보여준 마지막 댓글의 (작성 시각, id) 다음부터 읽는다. 앞에서 새 댓글이 생겨도 밀리거나
     * 겹치지 않고, 뒤로 갈수록 건너뛸 행이 늘어나는 일도 없다.
     */

    /** 원 댓글 첫 묶음 (최신순) */
    @Query("SELECT c FROM Comment c JOIN FETCH c.author "
            + "WHERE c.post.id = :postId AND c.parent IS NULL "
            + "ORDER BY c.createdAt DESC, c.id DESC")
    Slice<Comment> findRootComments(@Param("postId") Long postId, Pageable pageable);

    /** 원 댓글 다음 묶음: 이미 보여준 마지막 댓글보다 먼저 쓴 것 */
    @Query("SELECT c FROM Comment c JOIN FETCH c.author "
            + "WHERE c.post.id = :postId AND c.parent IS NULL "
            + "AND (c.createdAt < :beforeAt OR (c.createdAt = :beforeAt AND c.id < :beforeId)) "
            + "ORDER BY c.createdAt DESC, c.id DESC")
    Slice<Comment> findRootCommentsBefore(@Param("postId") Long postId,
                                          @Param("beforeAt") LocalDateTime beforeAt,
                                          @Param("beforeId") Long beforeId,
                                          Pageable pageable);

    /** 답글 첫 묶음 (대화 순서대로 오래된 것부터) */
    @Query("SELECT c FROM Comment c JOIN FETCH c.author "
            + "WHERE c.post.id = :postId AND c.parent.id = :parentId "
            + "ORDER BY c.createdAt ASC, c.id ASC")
    Slice<Comment> findReplies(@Param("postId") Long postId, @Param("parentId") Long parentId, Pageable pageable);

    /** 답글 다음 묶음: 이미 보여준 마지막 답글보다 나중에 쓴 것 */
    @Query("SELECT c FROM Comment c JOIN FETCH c.author "
            + "WHERE c.post.id = :postId AND c.parent.id = :parentId "
            + "AND (c.createdAt > :afterAt OR (c.createdAt = :afterAt AND c.id > :afterId)) "
            + "ORDER BY c.createdAt ASC, c.id ASC")
    Slice<Comment> findRepliesAfter(@Param("postId") Long postId,
                                    @Param("parentId") Long parentId,
                                    @Param("afterAt") LocalDateTime afterAt,
                                    @Param("afterId") Long afterId,
                                    Pageable pageable);

    /** 여러 원 댓글의 답글 수를 한 번에 조회 → [parentId, count] */
    @Query("SELECT c.parent.id, COUNT(c) FROM Comment c "
            + "WHERE c.parent.id IN :parentIds GROUP BY c.parent.id")
    List<Object[]> countRepliesByParentIds(@Param("parentIds") Collection<Long> parentIds);

    /** 게시글의 댓글 수 (답글 포함) */
    long countByPostId(Long postId);

    /** 이 댓글에 달린 답글 (원 댓글을 지울 때 함께 지운다) */
    List<Comment> findByParentId(Long parentId);

    /**
     * 한 사람이 쓴 댓글 전부와 댓글이 달린 글, 그 글의 작성자 (관리자 화면).
     * 화면에 보여주기만 하므로 엔티티로 읽지 않고 필요한 칸만 한 번에 받는다.
     * 엔티티로 읽으면 댓글마다 글과 글쓴이를 따로 읽어 쿼리가 1 + 글 수 + 글쓴이 수만큼 나간다.
     */
    @Query("SELECT new com.example.sns.dto.AdminCommentDto("
            + "c.id, c.content, c.createdAt, p.id, p.content, a.username) "
            + "FROM Comment c JOIN c.post p JOIN p.author a "
            + "WHERE c.author.id = :userId "
            + "ORDER BY c.createdAt DESC, c.id DESC")
    List<AdminCommentDto> findAdminCommentsByAuthor(@Param("userId") Long userId);

    /** 여러 게시글의 댓글 수를 한 번에 조회 → [postId, count] */
    @Query("SELECT c.post.id, COUNT(c) FROM Comment c "
            + "WHERE c.post.id IN :postIds GROUP BY c.post.id")
    List<Object[]> countByPostIds(@Param("postIds") Collection<Long> postIds);
}

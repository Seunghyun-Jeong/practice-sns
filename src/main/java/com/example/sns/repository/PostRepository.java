package com.example.sns.repository;

import com.example.sns.entity.Post;
import com.example.sns.entity.User;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findAllByOrderByCreatedAtDesc();
    List<Post> findAllByAuthor_IdOrderByCreatedAtDesc(Long userId);

    /**
     * 전체 피드 (페이지 단위).
     * 작성자를 함께 조회(fetch join)하고, 정지된 유저의 글은 DB에서 걸러낸다.
     * Slice 는 전체 개수를 세지 않고 "다음 페이지가 있는지"만 확인한다.
     */
    @Query("SELECT p FROM Post p JOIN FETCH p.author a "
            + "WHERE a.suspendedUntil IS NULL OR a.suspendedUntil <= :now "
            + "ORDER BY p.createdAt DESC")
    Slice<Post> findFeed(@Param("now") LocalDateTime now, Pageable pageable);

    /** 팔로잉 피드 (페이지 단위) */
    @Query("SELECT p FROM Post p JOIN FETCH p.author a "
            + "WHERE p.author IN :authors "
            + "AND (a.suspendedUntil IS NULL OR a.suspendedUntil <= :now) "
            + "ORDER BY p.createdAt DESC")
    Slice<Post> findFeedByAuthors(@Param("authors") Collection<User> authors,
                                  @Param("now") LocalDateTime now,
                                  Pageable pageable);

    /** 특정 해시태그가 달린 게시글 (페이지 단위) */
    @Query("SELECT p FROM Post p JOIN FETCH p.author a "
            + "WHERE p.id IN (SELECT ph.post.id FROM PostHashtag ph WHERE ph.hashtag.name = :tag) "
            + "AND (a.suspendedUntil IS NULL OR a.suspendedUntil <= :now) "
            + "ORDER BY p.createdAt DESC")
    Slice<Post> findFeedByTag(@Param("tag") String tag,
                              @Param("now") LocalDateTime now,
                              Pageable pageable);

    /** 프로필 피드 (작성자 fetch join) */
    @Query("SELECT p FROM Post p JOIN FETCH p.author a "
            + "WHERE a.id = :userId ORDER BY p.createdAt DESC")
    List<Post> findByAuthorIdWithAuthor(@Param("userId") Long userId);
}

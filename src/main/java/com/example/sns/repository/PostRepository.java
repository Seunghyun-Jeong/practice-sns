package com.example.sns.repository;

import com.example.sns.entity.Post;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findAllByOrderByCreatedAtDesc();
    List<Post> findAllByAuthor_IdOrderByCreatedAtDesc(Long userId);

    /*
     * 피드 세 종류(전체, 팔로잉, 해시태그)는 모두 최신순으로 한 번에 정해진 수만큼 끊어 읽는다.
     * 다음 묶음은 페이지 번호(OFFSET)가 아니라 이미 보여준 마지막 글의 (작성 시각, id)보다 먼저 쓴 글부터 읽는다.
     * 보는 사이 새 글이 올라와도 밀려서 같은 글이 다시 나오지 않고, 뒤로 갈수록 건너뛸 행이 늘지도 않는다.
     * 작성자는 함께 조회(fetch join)하고, 정지된 유저의 글은 DB에서 거른다.
     * 세 종류가 같은 조건과 순서를 써야 하므로 그 부분은 아래 상수 한 곳에서 가져다 쓴다.
     */

    /** 정지되지 않은 작성자의 글만 */
    String VISIBLE_AUTHOR = "(a.suspendedUntil IS NULL OR a.suspendedUntil <= :now) ";

    /** 이미 보여준 마지막 글보다 먼저 쓴 글 */
    String BEFORE_CURSOR = "AND (p.createdAt < :beforeAt OR (p.createdAt = :beforeAt AND p.id < :beforeId)) ";

    /** 최신순. 작성 시각이 같으면 id로 순서를 하나로 정한다 */
    String NEWEST_FIRST = "ORDER BY p.createdAt DESC, p.id DESC";

    /** 내 글과 내가 팔로우한 사람의 글. 팔로우 목록을 자바로 가져와 IN에 넣지 않고 서브쿼리로 DB에 맡긴다 */
    String FOLLOWING_AUTHORS = "(a.id = :userId OR a.id IN "
            + "(SELECT f.following.id FROM Follow f WHERE f.follower.id = :userId)) ";

    /** 해시태그가 달린 글 */
    String TAGGED = "p.id IN (SELECT ph.post.id FROM PostHashtag ph WHERE ph.hashtag.name = :tag) ";

    /** 전체 피드 첫 묶음 */
    @Query("SELECT p FROM Post p JOIN FETCH p.author a WHERE " + VISIBLE_AUTHOR + NEWEST_FIRST)
    Slice<Post> findFeed(@Param("now") LocalDateTime now, Pageable pageable);

    /** 전체 피드 다음 묶음 */
    @Query("SELECT p FROM Post p JOIN FETCH p.author a WHERE " + VISIBLE_AUTHOR + BEFORE_CURSOR + NEWEST_FIRST)
    Slice<Post> findFeedBefore(@Param("now") LocalDateTime now,
                               @Param("beforeAt") LocalDateTime beforeAt,
                               @Param("beforeId") Long beforeId,
                               Pageable pageable);

    /** 팔로잉 피드 첫 묶음 (내 글 포함) */
    @Query("SELECT p FROM Post p JOIN FETCH p.author a WHERE " + FOLLOWING_AUTHORS
            + "AND " + VISIBLE_AUTHOR + NEWEST_FIRST)
    Slice<Post> findFollowingFeed(@Param("userId") Long userId,
                                  @Param("now") LocalDateTime now,
                                  Pageable pageable);

    /** 팔로잉 피드 다음 묶음 */
    @Query("SELECT p FROM Post p JOIN FETCH p.author a WHERE " + FOLLOWING_AUTHORS
            + "AND " + VISIBLE_AUTHOR + BEFORE_CURSOR + NEWEST_FIRST)
    Slice<Post> findFollowingFeedBefore(@Param("userId") Long userId,
                                        @Param("now") LocalDateTime now,
                                        @Param("beforeAt") LocalDateTime beforeAt,
                                        @Param("beforeId") Long beforeId,
                                        Pageable pageable);

    /** 해시태그 피드 첫 묶음 */
    @Query("SELECT p FROM Post p JOIN FETCH p.author a WHERE " + TAGGED
            + "AND " + VISIBLE_AUTHOR + NEWEST_FIRST)
    Slice<Post> findFeedByTag(@Param("tag") String tag,
                              @Param("now") LocalDateTime now,
                              Pageable pageable);

    /** 해시태그 피드 다음 묶음 */
    @Query("SELECT p FROM Post p JOIN FETCH p.author a WHERE " + TAGGED
            + "AND " + VISIBLE_AUTHOR + BEFORE_CURSOR + NEWEST_FIRST)
    Slice<Post> findFeedByTagBefore(@Param("tag") String tag,
                                    @Param("now") LocalDateTime now,
                                    @Param("beforeAt") LocalDateTime beforeAt,
                                    @Param("beforeId") Long beforeId,
                                    Pageable pageable);

    /** 게시글 상세 (작성자 fetch join) */
    @Query("SELECT p FROM Post p JOIN FETCH p.author WHERE p.id = :postId")
    Optional<Post> findByIdWithAuthor(@Param("postId") Long postId);

    /** 프로필 피드 (작성자 fetch join) */
    @Query("SELECT p FROM Post p JOIN FETCH p.author a "
            + "WHERE a.id = :userId ORDER BY p.createdAt DESC")
    List<Post> findByAuthorIdWithAuthor(@Param("userId") Long userId);
}

package com.example.sns.repository;

import com.example.sns.entity.Post;
import com.example.sns.entity.PostLike;
import com.example.sns.entity.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    Optional<PostLike> findByPostAndUser(Post post, User user);
    boolean existsByPostAndUser(Post post, User user);
    long countByPost(Post post);

    /** 여러 게시글의 좋아요 수를 한 번에 조회 → [postId, count] */
    @Query("SELECT pl.post.id, COUNT(pl) FROM PostLike pl "
            + "WHERE pl.post.id IN :postIds GROUP BY pl.post.id")
    List<Object[]> countByPostIds(@Param("postIds") Collection<Long> postIds);

    /** 내가 좋아요를 누른 게시글 id 들을 한 번에 조회 */
    @Query("SELECT pl.post.id FROM PostLike pl "
            + "WHERE pl.user.id = :userId AND pl.post.id IN :postIds")
    List<Long> findLikedPostIds(@Param("userId") Long userId,
                                @Param("postIds") Collection<Long> postIds);
}

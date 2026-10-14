package com.example.sns.repository;

import com.example.sns.entity.Post;
import com.example.sns.entity.PostHashtag;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostHashtagRepository extends JpaRepository<PostHashtag, Long> {

    /** 게시글에 지금 달린 태그 연결. 태그 이름으로 비교하므로 태그까지 함께 읽는다 */
    @Query("SELECT ph FROM PostHashtag ph JOIN FETCH ph.hashtag WHERE ph.post = :post")
    List<PostHashtag> findByPostWithHashtag(@Param("post") Post post);

    /** 여러 게시글의 태그를 한 번에 조회 → [postId, 태그이름] */
    @Query("SELECT ph.post.id, ph.hashtag.name FROM PostHashtag ph "
            + "WHERE ph.post.id IN :postIds")
    List<Object[]> findTagNamesByPostIds(@Param("postIds") Collection<Long> postIds);
}

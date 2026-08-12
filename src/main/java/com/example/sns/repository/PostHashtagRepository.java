package com.example.sns.repository;

import com.example.sns.entity.Post;
import com.example.sns.entity.PostHashtag;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostHashtagRepository extends JpaRepository<PostHashtag, Long> {

    /** 게시글에 달린 태그 연결 (수정 시 다시 계산하기 위해 사용) */
    List<PostHashtag> findByPost(Post post);

    /** 여러 게시글의 태그를 한 번에 조회 → [postId, 태그이름] */
    @Query("SELECT ph.post.id, ph.hashtag.name FROM PostHashtag ph "
            + "WHERE ph.post.id IN :postIds")
    List<Object[]> findTagNamesByPostIds(@Param("postIds") Collection<Long> postIds);
}

package com.example.sns.repository;

import com.example.sns.entity.Post;
import com.example.sns.entity.User;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findAllByOrderByCreatedAtDesc();
    List<Post> findAllByAuthor_IdOrderByCreatedAtDesc(Long userId);

    /** 특정 작성자들의 게시글 (팔로잉 피드용) */
    List<Post> findAllByAuthorInOrderByCreatedAtDesc(Collection<User> authors);
}

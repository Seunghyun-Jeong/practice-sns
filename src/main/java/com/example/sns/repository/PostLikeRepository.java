package com.example.sns.repository;

import com.example.sns.entity.Post;
import com.example.sns.entity.PostLike;
import com.example.sns.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    Optional<PostLike> findByPostAndUser(Post post, User user);
    boolean existsByPostAndUser(Post post, User user);
    long countByPost(Post post);
}

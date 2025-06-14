package com.example.sns.repository;

import com.example.sns.entity.Post;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findAllByOrderByCreatedAtDesc();
    List<Post> findAllByAuthor_UsernameOrderByCreatedAtDesc(String username);
}

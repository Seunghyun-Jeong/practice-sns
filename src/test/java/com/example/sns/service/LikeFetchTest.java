package com.example.sns.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.sns.entity.Comment;
import com.example.sns.entity.CommentLike;
import com.example.sns.entity.Post;
import com.example.sns.entity.PostLike;
import com.example.sns.entity.User;
import com.example.sns.repository.CommentLikeRepository;
import com.example.sns.repository.CommentRepository;
import com.example.sns.repository.PostLikeRepository;
import com.example.sns.repository.PostRepository;
import com.example.sns.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDateTime;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좋아요를 여러 개 읽을 때 누른 사람을 따로 읽지 않는지 확인한다.
 *
 * 지금 화면은 좋아요를 개수와 id로만 쓰지만, 누른 사람이 바로 읽히게(EAGER) 매핑돼 있으면
 * 나중에 좋아요 목록을 읽는 순간 누른 사람마다 조회가 하나씩 더 나간다. 매핑이 되돌아가면 여기서 잡는다.
 */
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN"
})
@Transactional
class LikeFetchTest {

    private static final int LIKES = 10;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PostLikeRepository postLikeRepository;

    @Autowired
    private CommentLikeRepository commentLikeRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("좋아요 10개를 읽어도 누른 사람을 따로 읽지 않아 쿼리 1번이다")
    void 좋아요_목록은_한_번에_읽힌다() {
        User writer = createUser("writer");
        Post post = new Post();
        post.setAuthor(writer);
        post.setContent("글");
        post.setImageUrl("/uploads/test.png");
        post.setCreatedAt(LocalDateTime.now());
        postRepository.save(post);
        Comment comment = new Comment();
        comment.setAuthor(writer);
        comment.setPost(post);
        comment.setContent("댓글");
        comment.setCreatedAt(LocalDateTime.now());
        commentRepository.save(comment);

        for (int i = 0; i < LIKES; i++) {
            User liker = createUser("liker" + i);
            PostLike postLike = new PostLike();
            postLike.setPost(post);
            postLike.setUser(liker);
            postLikeRepository.save(postLike);
            CommentLike commentLike = new CommentLike();
            commentLike.setComment(comment);
            commentLike.setUser(liker);
            commentLikeRepository.save(commentLike);
        }
        entityManager.flush();
        entityManager.clear();

        long postLikeQueries = countQueries(() -> entityManager
                .createQuery("SELECT pl FROM PostLike pl WHERE pl.post.id = :id", PostLike.class)
                .setParameter("id", post.getId())
                .getResultList());
        long commentLikeQueries = countQueries(() -> entityManager
                .createQuery("SELECT cl FROM CommentLike cl WHERE cl.comment.id = :id", CommentLike.class)
                .setParameter("id", comment.getId())
                .getResultList());

        assertThat(postLikeQueries).as("게시글 좋아요 %d개 읽기 %d회", LIKES, postLikeQueries).isEqualTo(1);
        assertThat(commentLikeQueries).as("댓글 좋아요 %d개 읽기 %d회", LIKES, commentLikeQueries).isEqualTo(1);
    }

    private long countQueries(Runnable read) {
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        read.run();
        return statistics.getPrepareStatementCount();
    }

    private User createUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("password");
        user.setRole(User.Role.USER);
        return userRepository.save(user);
    }
}

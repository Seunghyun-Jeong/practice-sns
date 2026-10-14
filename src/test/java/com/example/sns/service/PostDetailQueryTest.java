package com.example.sns.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.sns.dto.CommentDto;
import com.example.sns.dto.PostDetailDto;
import com.example.sns.entity.Comment;
import com.example.sns.entity.Post;
import com.example.sns.entity.User;
import com.example.sns.repository.CommentRepository;
import com.example.sns.repository.PostRepository;
import com.example.sns.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDateTime;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시글 상세 조회의 쿼리 수 테스트.
 *
 * 댓글 작성자를 댓글마다 따로 읽으면 작성자 수만큼, 댓글을 전부 읽으면 댓글 수만큼 쿼리와 읽는 양이 늘어난다.
 * 댓글과 작성자가 적을 때와 많을 때 쿼리 수가 같은지로 확인한다.
 * 댓글 순서도 쿼리에서 정하므로, 순서가 맞는지도 함께 본다.
 */
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN"
})
@Transactional
class PostDetailQueryTest {

    @Autowired
    private PostService postService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("게시글 상세는 댓글과 작성자가 늘어도 쿼리 수가 같다")
    void 댓글_수와_작성자_수와_관계없이_쿼리_수가_같다() {
        long few = countDetailQueries(2);
        long many = countDetailQueries(30);

        assertThat(many)
                .as("댓글 2개일 때 %d회, 30개일 때 %d회", few, many)
                .isEqualTo(few);
    }

    @Test
    @DisplayName("원 댓글은 최신순이고, 같은 시각에 쓴 댓글은 나중에 쓴 것이 위에 온다")
    void 원_댓글은_최신순으로_나온다() {
        User poster = createUser("poster");
        Post post = createPost(poster);

        LocalDateTime sameTime = LocalDateTime.now();
        for (String name : List.of("first", "second", "third")) {
            createComment(post, createUser(name), sameTime);
        }
        entityManager.flush();
        entityManager.clear();

        PostDetailDto detail = postService.getPostDetail(post.getId(), poster.getId());

        assertThat(detail.getCommentCount()).isEqualTo(3);
        assertThat(detail.getComments()).extracting(CommentDto::getAuthor)
                .containsExactly("third", "second", "first");
    }

    /** 작성자가 모두 다른 원 댓글을 commentCount개 단 게시글을 만들고, 상세 조회에 나간 쿼리 수를 센다 */
    private long countDetailQueries(int commentCount) {
        User poster = createUser("p" + commentCount);
        Post post = createPost(poster);
        for (int i = 0; i < commentCount; i++) {
            createComment(post, createUser("c" + commentCount + "_" + i), LocalDateTime.now());
        }

        // 저장한 엔티티가 영속성 컨텍스트에 남아 있으면 조회 쿼리가 나가지 않으므로 비운다
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        postService.getPostDetail(post.getId(), poster.getId());
        return statistics.getPrepareStatementCount();
    }

    private Post createPost(User author) {
        Post post = new Post();
        post.setContent("게시글");
        post.setAuthor(author);
        return postRepository.save(post);
    }

    private void createComment(Post post, User author, LocalDateTime createdAt) {
        Comment comment = new Comment();
        comment.setPost(post);
        comment.setAuthor(author);
        comment.setContent("댓글");
        comment.setCreatedAt(createdAt);
        commentRepository.save(comment);
    }

    private User createUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("password");
        user.setRole(User.Role.USER);
        return userRepository.save(user);
    }
}

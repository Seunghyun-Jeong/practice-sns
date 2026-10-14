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
 * 댓글 작성자를 댓글마다 따로 읽으면 작성자 수만큼 쿼리가 늘어난다.
 * 작성자가 적을 때와 많을 때 쿼리 수가 같은지로 확인한다.
 * 댓글을 한 번에 가져오도록 바꾸면서 순서도 쿼리에서 정하게 됐으므로, 순서가 맞는지도 함께 본다.
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
    @DisplayName("게시글 상세는 댓글 작성자가 늘어도 쿼리 수가 같다")
    void 댓글_작성자_수와_관계없이_쿼리_수가_같다() {
        long fewAuthors = countDetailQueries(2);
        long manyAuthors = countDetailQueries(20);

        assertThat(manyAuthors)
                .as("작성자 2명일 때 %d회, 20명일 때 %d회", fewAuthors, manyAuthors)
                .isEqualTo(fewAuthors);
    }

    @Test
    @DisplayName("같은 시각에 쓴 댓글도 쓴 순서대로 작성자와 함께 나온다")
    void 댓글이_쓴_순서대로_나온다() {
        User poster = createUser("poster");
        Post post = new Post();
        post.setContent("게시글");
        post.setAuthor(poster);
        post = postRepository.save(post);

        LocalDateTime sameTime = LocalDateTime.now();
        for (String name : List.of("first", "second", "third")) {
            Comment comment = new Comment();
            comment.setPost(post);
            comment.setAuthor(createUser(name));
            comment.setContent(name);
            comment.setCreatedAt(sameTime);
            commentRepository.save(comment);
        }
        entityManager.flush();
        entityManager.clear();

        PostDetailDto detail = postService.getPostDetail(post.getId(), poster.getId());

        assertThat(detail.getCommentCount()).isEqualTo(3);
        assertThat(detail.getComments()).extracting(CommentDto::getAuthor)
                .containsExactly("first", "second", "third");
    }

    /** 작성자가 모두 다른 댓글을 authorCount개 단 게시글을 만들고, 상세 조회에 나간 쿼리 수를 센다 */
    private long countDetailQueries(int authorCount) {
        User poster = createUser("p" + authorCount);

        Post post = new Post();
        post.setContent("게시글");
        post.setAuthor(poster);
        post = postRepository.save(post);

        for (int i = 0; i < authorCount; i++) {
            Comment comment = new Comment();
            comment.setPost(post);
            comment.setAuthor(createUser("c" + authorCount + "_" + i));
            comment.setContent("댓글 " + i);
            comment.setCreatedAt(LocalDateTime.now());
            commentRepository.save(comment);
        }

        // 저장한 엔티티가 영속성 컨텍스트에 남아 있으면 조회 쿼리가 나가지 않으므로 비운다
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        postService.getPostDetail(post.getId(), poster.getId());
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

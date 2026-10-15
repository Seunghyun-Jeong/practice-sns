package com.example.sns.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.sns.dto.AdminCommentDto;
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
 * 관리자 화면(사용자별 작성 댓글) 조회 테스트.
 *
 * 신고된 사람이 쓴 댓글을 전부 보여주면서 댓글이 달린 글과 그 글의 작성자도 함께 보여준다.
 * 여러 사람의 글에 댓글을 단 사람일수록 글과 글쓴이를 따로 읽으면 쿼리가 늘어나므로,
 * 댓글이 몇 개의 글에 흩어져 있든 쿼리 수가 같아야 한다.
 */
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN"
})
@Transactional
class AdminCommentQueryTest {

    @Autowired
    private CommentService commentService;

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
    @DisplayName("댓글마다 달린 글과 그 글의 작성자를 함께 최신순으로 보여준다")
    void 댓글과_원문_글을_함께_보여준다() {
        User target = createUser("target");
        User alice = createUser("alice");
        User bob = createUser("bob");
        Post alicePost = createPost(alice, "alice 글");
        Post bobPost = createPost(bob, "bob 글");
        LocalDateTime now = LocalDateTime.now();
        createComment(target, alicePost, "첫 댓글", now);
        createComment(target, bobPost, "둘째 댓글", now.plusSeconds(1));
        createComment(alice, alicePost, "다른 사람 댓글", now.plusSeconds(2));
        flushAndClear();

        List<AdminCommentDto> comments = commentService.getCommentsByUser(target.getId());

        assertThat(comments).extracting(AdminCommentDto::getContent).containsExactly("둘째 댓글", "첫 댓글");
        assertThat(comments).extracting(AdminCommentDto::getPostContent).containsExactly("bob 글", "alice 글");
        assertThat(comments).extracting(AdminCommentDto::getPostAuthor).containsExactly("bob", "alice");
    }

    @Test
    @DisplayName("댓글이 흩어진 글과 글쓴이가 늘어도 쿼리 수가 같다")
    void 쿼리_수는_댓글이_달린_글_수와_관계없다() {
        long few = countQueries(3);
        long many = countQueries(30);

        assertThat(many)
                .as("서로 다른 글 3개일 때 %d회, 30개일 때 %d회", few, many)
                .isEqualTo(few)
                .isEqualTo(1);
    }

    /** 서로 다른 n명이 쓴 글 n개에 댓글을 하나씩 단 사람을 만들고, 그 사람의 댓글을 읽을 때 나간 쿼리 수를 센다 */
    private long countQueries(int n) {
        User target = createUser("t" + n);
        for (int i = 0; i < n; i++) {
            Post post = createPost(createUser("w" + n + "_" + i), "글 " + i);
            createComment(target, post, "댓글 " + i, LocalDateTime.now());
        }
        flushAndClear();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        commentService.getCommentsByUser(target.getId());
        return statistics.getPrepareStatementCount();
    }

    private void createComment(User author, Post post, String content, LocalDateTime createdAt) {
        Comment comment = new Comment();
        comment.setAuthor(author);
        comment.setPost(post);
        comment.setContent(content);
        comment.setCreatedAt(createdAt);
        commentRepository.save(comment);
    }

    private Post createPost(User author, String content) {
        Post post = new Post();
        post.setAuthor(author);
        post.setContent(content);
        post.setImageUrl("/uploads/test.png");
        post.setCreatedAt(LocalDateTime.now());
        return postRepository.save(post);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private User createUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("password");
        user.setRole(User.Role.USER);
        return userRepository.save(user);
    }
}

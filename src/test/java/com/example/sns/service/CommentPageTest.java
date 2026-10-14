package com.example.sns.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.sns.dto.CommentDto;
import com.example.sns.dto.CommentPageDto;
import com.example.sns.dto.PostDetailDto;
import com.example.sns.entity.Comment;
import com.example.sns.entity.Post;
import com.example.sns.entity.User;
import com.example.sns.exception.NotFoundException;
import com.example.sns.repository.CommentRepository;
import com.example.sns.repository.PostRepository;
import com.example.sns.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시글 상세의 댓글 묶음 테스트.
 *
 * 원 댓글은 20개씩 최신순으로, 답글은 원 댓글마다 접어 두었다가 오래된 순으로 받는다.
 * 다음 묶음은 이미 받은 마지막 댓글의 (작성 시각, id) 다음부터라서, 시각이 같은 댓글이 묶음 경계에
 * 걸려도 겹치거나 빠지면 안 된다.
 */
@SpringBootTest
@Transactional
class CommentPageTest {

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

    private User writer;
    private Post post;

    @BeforeEach
    void setUp() {
        writer = createUser("writer");
        post = new Post();
        post.setContent("게시글");
        post.setAuthor(writer);
        post = postRepository.save(post);
    }

    @Test
    @DisplayName("원 댓글은 20개씩 받고, 시각이 같아도 다음 묶음과 겹치거나 빠지지 않는다")
    void 원_댓글_묶음은_겹치지도_빠지지도_않는다() {
        LocalDateTime sameTime = LocalDateTime.now();
        List<Long> written = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            written.add(createComment(null, sameTime).getId());
        }
        flushAndClear();

        CommentPageDto first = postService.getRootComments(post.getId(), writer.getId(), null, null);
        CommentDto last = first.getComments().get(first.getComments().size() - 1);
        CommentPageDto second = postService.getRootComments(post.getId(), writer.getId(),
                LocalDateTime.parse(last.getCreatedAt()), last.getId());

        assertThat(first.getComments()).hasSize(20);
        assertThat(first.isHasNext()).isTrue();
        assertThat(second.getComments()).hasSize(5);
        assertThat(second.isHasNext()).isFalse();

        List<Long> received = new ArrayList<>();
        first.getComments().forEach(c -> received.add(c.getId()));
        second.getComments().forEach(c -> received.add(c.getId()));
        assertThat(received).containsExactlyInAnyOrderElementsOf(written).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("원 댓글 목록에는 답글이 섞이지 않고 답글 수만 담긴다")
    void 원_댓글에는_답글_수만_담긴다() {
        Comment root = createComment(null, LocalDateTime.now());
        createComment(root, LocalDateTime.now());
        createComment(root, LocalDateTime.now());
        flushAndClear();

        PostDetailDto detail = postService.getPostDetail(post.getId(), writer.getId());

        assertThat(detail.getComments()).extracting(CommentDto::getId).containsExactly(root.getId());
        assertThat(detail.getComments().get(0).getReplyCount()).isEqualTo(2);
        assertThat(detail.getCommentCount()).isEqualTo(3);   // 답글도 댓글 수에 들어간다
    }

    @Test
    @DisplayName("답글은 오래된 순으로 받고, 이미 받은 답글 다음부터 이어서 받는다")
    void 답글은_오래된_순으로_이어서_받는다() {
        Comment root = createComment(null, LocalDateTime.now());
        LocalDateTime base = LocalDateTime.now();
        Comment r1 = createComment(root, base);
        Comment r2 = createComment(root, base.plusSeconds(1));
        Comment r3 = createComment(root, base.plusSeconds(2));
        flushAndClear();

        CommentPageDto all = postService.getReplies(post.getId(), root.getId(), writer.getId(), null, null);
        CommentDto second = all.getComments().get(1);
        CommentPageDto rest = postService.getReplies(post.getId(), root.getId(), writer.getId(),
                LocalDateTime.parse(second.getCreatedAt()), second.getId());

        assertThat(all.getComments()).extracting(CommentDto::getId)
                .containsExactly(r1.getId(), r2.getId(), r3.getId());
        assertThat(rest.getComments()).extracting(CommentDto::getId).containsExactly(r3.getId());
    }

    @Test
    @DisplayName("방금 쓴 댓글 하나를 받을 수 있고, 다른 게시글 주소로는 찾을 수 없다")
    void 댓글_하나를_받는다() {
        Comment root = createComment(null, LocalDateTime.now());
        Post other = new Post();
        other.setContent("다른 게시글");
        other.setAuthor(writer);
        Long otherPostId = postRepository.save(other).getId();
        flushAndClear();

        assertThat(postService.getComment(post.getId(), root.getId(), writer.getId()).getId())
                .isEqualTo(root.getId());
        assertThatThrownBy(() -> postService.getComment(otherPostId, root.getId(), writer.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    private Comment createComment(Comment parent, LocalDateTime createdAt) {
        Comment comment = new Comment();
        comment.setPost(post);
        comment.setAuthor(writer);
        comment.setParent(parent);
        comment.setContent("댓글");
        comment.setCreatedAt(createdAt);
        return commentRepository.save(comment);
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

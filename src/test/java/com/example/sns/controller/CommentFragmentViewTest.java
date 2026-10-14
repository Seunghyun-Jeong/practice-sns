package com.example.sns.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sns.dto.CommentDto;
import com.example.sns.dto.CommentPageDto;
import com.example.sns.entity.Comment;
import com.example.sns.entity.Post;
import com.example.sns.entity.User;
import com.example.sns.repository.CommentRepository;
import com.example.sns.repository.PostRepository;
import com.example.sns.repository.UserRepository;
import com.example.sns.service.PostService;
import com.example.sns.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시글 상세의 댓글 조각 테스트.
 *
 * 모달을 처음 열 때와 이어 받는 묶음, 답글 묶음, 방금 쓴 댓글이 같은 조각으로 그려지는지 본다.
 * 템플릿 오류는 실행해 봐야 드러나므로 실제로 그려서 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommentFragmentViewTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PostService postService;

    @Autowired
    private JwtUtil jwtUtil;

    private User writer;
    private Post post;

    @BeforeEach
    void setUp() {
        writer = new User();
        writer.setUsername("writer");
        writer.setPassword("password");
        writer.setRole(User.Role.USER);
        writer = userRepository.save(writer);

        post = new Post();
        post.setContent("게시글");
        post.setAuthor(writer);
        post = postRepository.save(post);
    }

    @Test
    @DisplayName("모달은 원 댓글 첫 묶음만 그리고, 다음 묶음은 이어서 받는다")
    void 모달과_다음_묶음() throws Exception {
        for (int i = 0; i < 25; i++) {
            createComment(null);
        }

        String modal = mockMvc.perform(get("/posts/{id}/modal", post.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        CommentPageDto first = postService.getRootComments(post.getId(), null, null, null);
        CommentDto last = first.getComments().get(first.getComments().size() - 1);
        String next = mockMvc.perform(get("/posts/{id}/comments", post.getId())
                        .param("beforeAt", last.getCreatedAt())
                        .param("beforeId", String.valueOf(last.getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(count(modal, "class=\"comment-thread\"")).isEqualTo(20);
        assertThat(modal).contains("data-has-next=\"true\"");
        assertThat(count(next, "class=\"comment-thread\"")).isEqualTo(5);
        assertThat(next).contains("data-has-next=\"false\"");
    }

    @Test
    @DisplayName("답글은 펼칠 때 따로 받고, 방금 쓴 답글은 한 줄만 받는다")
    void 답글_묶음과_방금_쓴_답글() throws Exception {
        Comment root = createComment(null);
        createComment(root);
        Comment latest = createComment(root);

        String replies = mockMvc.perform(get("/posts/{postId}/comments/{parentId}/replies", post.getId(), root.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String one = mockMvc.perform(get("/posts/{postId}/comments/{id}", post.getId(), latest.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(count(replies, "comment-reply")).isEqualTo(2);
        assertThat(one).contains("comment-reply").doesNotContain("comment-thread");
    }

    @Test
    @DisplayName("댓글을 쓰면 새 댓글의 id를 돌려준다")
    void 댓글을_쓰면_id를_돌려준다() throws Exception {
        mockMvc.perform(post("/api/posts/{postId}/comments", post.getId())
                        .cookie(new Cookie("JWT_TOKEN",
                                jwtUtil.generateToken(writer.getId(), writer.getUsername(), writer.getRole().name())))
                        .contentType("application/json")
                        .content("{\"content\":\"새 댓글\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber());
    }

    private Comment createComment(Comment parent) {
        Comment comment = new Comment();
        comment.setPost(post);
        comment.setAuthor(writer);
        comment.setParent(parent);
        comment.setContent("댓글");
        comment.setCreatedAt(LocalDateTime.now());
        return commentRepository.save(comment);
    }

    private int count(String html, String token) {
        return html.split(Pattern.quote(token), -1).length - 1;
    }
}

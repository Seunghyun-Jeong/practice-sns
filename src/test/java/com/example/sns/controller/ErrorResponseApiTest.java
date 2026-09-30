package com.example.sns.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sns.entity.Comment;
import com.example.sns.entity.Post;
import com.example.sns.entity.User;
import com.example.sns.repository.CommentRepository;
import com.example.sns.repository.PostRepository;
import com.example.sns.repository.UserRepository;
import com.example.sns.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실패 응답이 무엇으로 나가는지 확인한다.
 *
 * 예전에는 서비스가 없는 자원과 권한 없음을 모두 IllegalArgumentException으로 던져서,
 * 컨트롤러가 그걸 잡아 제각각 400이나 404로 바꿔 보냈다. 잡는 것을 빠뜨린 곳은 그대로
 * 500이 나갔다. 예외 종류를 나누고 처리를 GlobalExceptionHandler 한 곳으로 모은 뒤로
 * 같은 상황이 어디서든 같은 코드로 나가야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ErrorResponseApiTest {

    private static final long 없는_ID = 999_999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    private User author;
    private User stranger;
    private Cookie authorCookie;
    private Cookie strangerCookie;
    private Post post;
    private Comment comment;

    @BeforeEach
    void setUp() {
        author = createUser("author1");
        stranger = createUser("stranger1");
        authorCookie = createCookie(author);
        strangerCookie = createCookie(stranger);

        post = new Post();
        post.setContent("본문");
        post.setImageUrl("/uploads/test.png");
        post.setAuthor(author);
        post = postRepository.save(post);

        comment = new Comment();
        comment.setContent("댓글");
        comment.setAuthor(author);
        comment.setPost(post);
        comment = commentRepository.save(comment);
    }

    @Test
    @DisplayName("없는 댓글을 수정하면 500이 아니라 404를 준다")
    void 없는_댓글_수정은_404() throws Exception {
        mockMvc.perform(put("/api/posts/{postId}/comments/{id}", post.getId(), 없는_ID)
                        .cookie(authorCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"고쳐보기\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("댓글을 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("없는 게시글에 좋아요를 누르면 500이 아니라 404를 준다")
    void 없는_게시글_좋아요는_404() throws Exception {
        mockMvc.perform(post("/api/posts/{postId}/like", 없는_ID).cookie(authorCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("게시글을 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("없는 게시글을 삭제하면 404를 준다")
    void 없는_게시글_삭제는_404() throws Exception {
        mockMvc.perform(delete("/api/posts/{id}", 없는_ID).cookie(authorCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("게시글을 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("남의 게시글을 삭제하면 403과 사유를 JSON으로 준다")
    void 남의_게시글_삭제는_403() throws Exception {
        mockMvc.perform(delete("/api/posts/{id}", post.getId()).cookie(strangerCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("게시글 삭제 권한이 없습니다."));
    }

    @Test
    @DisplayName("남의 댓글을 수정하면 403을 준다")
    void 남의_댓글_수정은_403() throws Exception {
        mockMvc.perform(put("/api/posts/{postId}/comments/{id}", post.getId(), comment.getId())
                        .cookie(strangerCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"가로채기\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("본인이 작성한 댓글만 수정할 수 있습니다."));
    }

    @Test
    @DisplayName("없는 유저를 팔로우하면 404를 준다")
    void 없는_유저_팔로우는_404() throws Exception {
        mockMvc.perform(post("/api/users/{id}/follow", 없는_ID).cookie(authorCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("팔로우할 유저를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("공백만 있는 댓글은 400을 준다")
    void 빈_댓글은_400() throws Exception {
        mockMvc.perform(post("/api/posts/{postId}/comments", post.getId())
                        .cookie(authorCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("내용을 입력해주세요."));
    }

    @Test
    @DisplayName("댓글은 칸 크기까지는 등록되고, 한 글자라도 넘으면 DB까지 가지 않고 400을 준다")
    void 댓글_길이_경계() throws Exception {
        mockMvc.perform(post("/api/posts/{postId}/comments", post.getId())
                        .cookie(authorCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contentJson("가".repeat(Comment.MAX_CONTENT_LENGTH))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/posts/{postId}/comments", post.getId())
                        .cookie(authorCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contentJson("가".repeat(Comment.MAX_CONTENT_LENGTH + 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("내용은 " + Comment.MAX_CONTENT_LENGTH + "자까지 쓸 수 있습니다."));
    }

    @Test
    @DisplayName("길이는 앞뒤 공백을 뺀 뒤에 센다")
    void 댓글_앞뒤_공백은_길이에서_빠진다() throws Exception {
        String padded = "  " + "가".repeat(Comment.MAX_CONTENT_LENGTH) + "  ";

        mockMvc.perform(put("/api/posts/{postId}/comments/{id}", post.getId(), comment.getId())
                        .cookie(authorCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contentJson(padded)))
                .andExpect(status().isOk());

        Comment saved = commentRepository.findById(comment.getId()).orElseThrow();
        assertThat(saved.getContent()).isEqualTo(padded.trim());
    }

    @Test
    @DisplayName("게시글 본문을 비워서 수정하면 400을 준다")
    void 게시글_본문을_비우면_400() throws Exception {
        mockMvc.perform(put("/api/posts/{id}", post.getId())
                        .cookie(authorCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("내용을 입력해주세요."));
    }

    @Test
    @DisplayName("게시글 본문이 한도를 넘으면 400을 준다")
    void 게시글_본문_한도_초과는_400() throws Exception {
        mockMvc.perform(put("/api/posts/{id}", post.getId())
                        .cookie(authorCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contentJson("가".repeat(Post.MAX_CONTENT_LENGTH + 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("내용은 " + Post.MAX_CONTENT_LENGTH + "자까지 쓸 수 있습니다."));
    }

    private String contentJson(String content) {
        return "{\"content\":\"" + content + "\"}";
    }

    private User createUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("Test1234!"));
        user.setRole(User.Role.USER);
        return userRepository.save(user);
    }

    private Cookie createCookie(User user) {
        return new Cookie("JWT_TOKEN",
                jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole().name()));
    }
}

package com.example.sns.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sns.entity.Comment;
import com.example.sns.entity.Notification;
import com.example.sns.entity.Post;
import com.example.sns.entity.User;
import com.example.sns.repository.CommentRepository;
import com.example.sns.repository.NotificationRepository;
import com.example.sns.repository.PostRepository;
import com.example.sns.repository.UserRepository;
import com.example.sns.util.JwtUtil;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대댓글과 멘션 API 테스트.
 *
 * 답글은 한 단만 쌓이고, 알림은 본문의 @아이디뿐 아니라 답글의 상대에게도 가야 한다.
 * 자동으로 채워진 멘션을 지우고 답글을 남겨도 상대가 알 수 있어야 하기 때문이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReplyMentionApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private EntityManager entityManager;

    private User poster;      // 게시글 작성자
    private User commenter;   // 원 댓글 작성자
    private User replier;     // 답글 작성자
    private Post post;
    private Comment rootComment;

    @BeforeEach
    void setUp() {
        poster = createUser("poster");
        commenter = createUser("commenter");
        replier = createUser("replier");

        post = new Post();
        post.setContent("게시글");
        post.setImageUrl("/uploads/test.png");
        post.setAuthor(poster);
        post = postRepository.save(post);

        rootComment = new Comment();
        rootComment.setPost(post);
        rootComment.setAuthor(commenter);
        rootComment.setContent("원 댓글");
        rootComment.setCreatedAt(LocalDateTime.now());
        rootComment = commentRepository.save(rootComment);
    }

    private User createUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("Test1234!"));
        user.setRole(User.Role.USER);
        return userRepository.save(user);
    }

    private Cookie cookieOf(User user) {
        return new Cookie("JWT_TOKEN",
                jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole().name()));
    }

    /** 댓글 등록 요청 본문 */
    private String commentBody(String content, Long parentId) {
        return parentId == null
                ? String.format("{\"content\":\"%s\"}", content)
                : String.format("{\"content\":\"%s\",\"parentId\":%d}", content, parentId);
    }

    private void writeComment(User author, String content, Long parentId) throws Exception {
        String body = commentBody(content, parentId);

        mockMvc.perform(post("/api/posts/{postId}/comments", post.getId())
                        .cookie(cookieOf(author))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk());

        endRequest();
    }

    /**
     * 요청이 끝난 것처럼 만든다.
     *
     * 테스트는 트랜잭션 하나로 묶여 있어서 방금 저장한 엔티티가 그대로 남아 있는데,
     * 그 상태에서는 알림 목록 같은 컬렉션이 비어 있는 채로 초기화되어 실제와 달라진다.
     * 진짜 요청은 매번 트랜잭션이 따로라 엔티티를 새로 읽어 오므로 그 상황을 맞춰준다.
     */
    private void endRequest() {
        entityManager.flush();
        entityManager.clear();
        post = postRepository.findById(post.getId()).orElseThrow();
        rootComment = commentRepository.findById(rootComment.getId()).orElseThrow();
    }

    private List<Notification> notificationsFor(User user, Notification.Type type) {
        return notificationRepository.findAll().stream()
                .filter(n -> n.getRecipient().getId().equals(user.getId()) && n.getType() == type)
                .toList();
    }

    private Comment latestComment() {
        List<Comment> all = commentRepository.findByPostIdOrderByCreatedAtAsc(post.getId());
        return all.get(all.size() - 1);
    }

    @Test
    @DisplayName("답글을 달면 원 댓글에 붙는다")
    void 답글은_원_댓글에_붙는다() throws Exception {
        writeComment(replier, "@commenter 답글입니다", rootComment.getId());

        Comment reply = latestComment();
        assertThat(reply.isReply()).isTrue();
        assertThat(reply.getParent().getId()).isEqualTo(rootComment.getId());
    }

    @Test
    @DisplayName("답글에 답글을 달아도 단이 깊어지지 않고 같은 원 댓글에 붙는다")
    void 답글의_답글도_같은_부모에_붙는다() throws Exception {
        writeComment(replier, "@commenter 답글입니다", rootComment.getId());
        Comment firstReply = latestComment();

        // 답글을 상대로 다시 답글을 단다
        writeComment(commenter, "@replier 또 답글", firstReply.getId());

        Comment secondReply = latestComment();
        assertThat(secondReply.getParent().getId()).isEqualTo(rootComment.getId());
    }

    @Test
    @DisplayName("답글을 달면 원 댓글 작성자에게 알림이 간다")
    void 답글은_원_댓글_작성자에게_알린다() throws Exception {
        writeComment(replier, "@commenter 답글입니다", rootComment.getId());

        assertThat(notificationsFor(commenter, Notification.Type.MENTION)).hasSize(1);
    }

    @Test
    @DisplayName("자동으로 채워진 멘션을 지우고 답글을 달아도 상대는 알림을 받는다")
    void 멘션을_지워도_답글_알림은_간다() throws Exception {
        // 본문만 보고 판단하면 여기서 알림이 사라져 답을 받은 사람이 모르게 된다
        writeComment(replier, "멘션을 지운 답글", rootComment.getId());

        assertThat(notificationsFor(commenter, Notification.Type.MENTION)).hasSize(1);
    }

    @Test
    @DisplayName("답글이 아닌 댓글에 적은 멘션도 알림이 간다")
    void 일반_댓글의_멘션도_알린다() throws Exception {
        writeComment(replier, "@commenter 확인해주세요", null);

        assertThat(notificationsFor(commenter, Notification.Type.MENTION)).hasSize(1);
    }

    @Test
    @DisplayName("같은 사람을 여러 댓글에서 언급하면 그때마다 알림이 간다")
    void 멘션_알림은_매번_간다() throws Exception {
        // 팔로우나 좋아요와 달리 멘션은 중복으로 걸러지면 안 된다
        writeComment(replier, "@commenter 첫번째", null);
        writeComment(replier, "@commenter 두번째", null);

        assertThat(notificationsFor(commenter, Notification.Type.MENTION)).hasSize(2);
    }

    @Test
    @DisplayName("게시글 작성자가 멘션 대상이면 댓글 알림을 따로 보내지 않는다")
    void 멘션과_댓글_알림이_겹치지_않는다() throws Exception {
        writeComment(replier, "@poster 보세요", null);

        assertThat(notificationsFor(poster, Notification.Type.MENTION)).hasSize(1);
        assertThat(notificationsFor(poster, Notification.Type.COMMENT)).isEmpty();
    }

    @Test
    @DisplayName("자기 자신을 언급하면 알림이 오지 않는다")
    void 자기_멘션은_알리지_않는다() throws Exception {
        writeComment(replier, "@replier 혼잣말", null);

        assertThat(notificationsFor(replier, Notification.Type.MENTION)).isEmpty();
    }

    @Test
    @DisplayName("없는 아이디를 적으면 알림이 생기지 않는다")
    void 없는_아이디는_알림이_없다() throws Exception {
        writeComment(replier, "@nobody 있나요", null);

        assertThat(notificationRepository.findAll().stream()
                .filter(n -> n.getType() == Notification.Type.MENTION)
                .toList()).isEmpty();
    }

    @Test
    @DisplayName("정지된 계정을 언급하면 알림을 보내지 않는다")
    void 정지된_계정은_멘션_알림을_받지_않는다() throws Exception {
        commenter.setSuspendedUntil(LocalDateTime.now().plusDays(1));
        userRepository.save(commenter);

        writeComment(replier, "@commenter 보세요", null);

        assertThat(notificationsFor(commenter, Notification.Type.MENTION)).isEmpty();
    }

    @Test
    @DisplayName("원 댓글을 지우면 답글도 함께 사라진다")
    void 원_댓글을_지우면_답글도_사라진다() throws Exception {
        writeComment(replier, "@commenter 답글입니다", rootComment.getId());
        assertThat(commentRepository.findByPostIdOrderByCreatedAtAsc(post.getId())).hasSize(2);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/posts/{postId}/comments/{id}", post.getId(), rootComment.getId())
                        .cookie(cookieOf(commenter)))
                .andExpect(status().isOk());

        assertThat(commentRepository.findByPostIdOrderByCreatedAtAsc(post.getId())).isEmpty();
    }

    @Test
    @DisplayName("다른 게시글의 댓글에는 답글을 달 수 없다")
    void 다른_게시글의_댓글에는_답글을_못_단다() throws Exception {
        Post another = new Post();
        another.setContent("다른 게시글");
        another.setImageUrl("/uploads/other.png");
        another.setAuthor(poster);
        another = postRepository.save(another);

        mockMvc.perform(post("/api/posts/{postId}/comments", another.getId())
                        .cookie(cookieOf(replier))
                        .contentType("application/json")
                        .content(commentBody("답글", rootComment.getId())))
                .andExpect(status().isBadRequest());
    }
}

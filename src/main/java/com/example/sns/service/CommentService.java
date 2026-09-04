package com.example.sns.service;

import com.example.sns.dto.AdminCommentDto;
import com.example.sns.dto.CommentDto;
import com.example.sns.dto.CommentUpdateRequest;
import com.example.sns.entity.Comment;
import com.example.sns.entity.Notification;
import com.example.sns.entity.Post;
import com.example.sns.entity.User;
import com.example.sns.repository.CommentRepository;
import com.example.sns.repository.PostRepository;
import com.example.sns.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommentService {
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final MentionService mentionService;

    public void addComment(Long postId, CommentDto dto, String username) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("해당 게시글이 존재하지 않습니다."));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저가 존재하지 않습니다."));

        Comment parent = resolveParent(dto.getParentId(), post);

        Comment comment = new Comment();
        comment.setPost(post);
        comment.setAuthor(user);
        comment.setContent(dto.getContent());
        comment.setParent(parent);
        comment.setCreatedAt(LocalDateTime.now());

        commentRepository.save(comment);

        Set<User> mentioned = collectMentionTargets(comment, parent, user);
        for (User target : mentioned) {
            notificationService.notify(target, user, Notification.Type.MENTION, post, comment);
        }

        // 게시글 작성자에게 알림 (어떤 댓글인지도 함께 남긴다).
        // 이미 멘션으로 알림을 받는 사람이면 같은 댓글로 두 번 알리지 않는다.
        if (!mentioned.contains(post.getAuthor())) {
            notificationService.notify(post.getAuthor(), user, Notification.Type.COMMENT, post, comment);
        }
    }

    /**
     * 답글의 부모를 찾는다.
     *
     * 답글에 답글을 달면 새 단을 만들지 않고 같은 부모에 붙인다. 누구에게 답한 것인지는
     * 본문의 멘션이 알려주기 때문에 깊이를 늘릴 이유가 없고, 늘리면 화면이 좁은 곳에서
     * 들여쓰기가 감당이 안 된다.
     */
    private Comment resolveParent(Long parentId, Post post) {
        if (parentId == null) {
            return null;
        }

        Comment parent = commentRepository.findById(parentId)
                .orElseThrow(() -> new IllegalArgumentException("답글을 달 댓글이 존재하지 않습니다."));

        if (!parent.getPost().getId().equals(post.getId())) {
            throw new IllegalArgumentException("다른 게시글의 댓글에는 답글을 달 수 없습니다.");
        }

        return parent.isReply() ? parent.getParent() : parent;
    }

    /**
     * 알림을 받을 사람들.
     *
     * 본문에 적힌 @아이디뿐 아니라 답글의 부모 작성자도 넣는다. 본문만 보고 판단하면,
     * 자동으로 채워진 멘션을 지우고 답글을 남겼을 때 정작 답을 받은 사람이 모르게 된다.
     * 자기 자신과 정지된 계정은 제외한다.
     */
    private Set<User> collectMentionTargets(Comment comment, Comment parent, User author) {
        Set<User> targets = new LinkedHashSet<>();

        if (parent != null) {
            targets.add(parent.getAuthor());
        }
        targets.addAll(mentionService.findMentionedUsers(comment.getContent()));

        targets.removeIf(target -> target.getId().equals(author.getId()) || target.isSuspended());
        return targets;
    }

    public void updateComment(Long commentId, CommentUpdateRequest request, String username) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글이 존재하지 않습니다."));

        if (!comment.getAuthor().getUsername().equals(username)) {
            throw new AccessDeniedException("본인이 작성한 댓글만 수정할 수 있습니다.");
        }

        comment.setContent(request.getContent());
        comment.setUpdatedAt(LocalDateTime.now());
        commentRepository.save(comment);
    }

    public List<AdminCommentDto> getCommentsByUser(Long userId) {
        return commentRepository.findByAuthor_IdOrderByCreatedAtDesc(userId).stream()
                .map(comment -> new AdminCommentDto(
                        comment.getId(),
                        comment.getContent(),
                        comment.getCreatedAt().toString(),
                        comment.getPost().getId(),
                        comment.getPost().getContent(),
                        comment.getPost().getAuthor().getUsername()
                ))
                .collect(Collectors.toList());
    }

    public void deleteComment(Long commentId, String username, String role) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 삭제할 수 없습니다."));

        if (!comment.getAuthor().getUsername().equals(username) && !"ADMIN".equals(role)) {
            throw new SecurityException("댓글 삭제 권한이 없습니다.");
        }

        // 답글을 먼저 지운다. 부모가 없어진 답글은 누구에게 한 말인지 알 수 없다.
        // 엔티티의 컬렉션에 맡기지 않는 이유는, 답글이 별개의 요청으로 저장되다 보니
        // 부모가 들고 있는 목록이 비어 있는 채로 남아 실제와 어긋날 수 있기 때문이다.
        List<Comment> replies = commentRepository.findByParentId(commentId);
        if (!replies.isEmpty()) {
            commentRepository.deleteAll(replies);
        }

        commentRepository.delete(comment);
    }
}

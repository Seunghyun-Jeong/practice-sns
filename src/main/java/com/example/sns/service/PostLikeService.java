package com.example.sns.service;

import com.example.sns.entity.Notification;
import com.example.sns.entity.Post;
import com.example.sns.entity.PostLike;
import com.example.sns.entity.User;
import com.example.sns.event.NotificationEvent;
import com.example.sns.exception.NotFoundException;
import com.example.sns.repository.PostLikeRepository;
import com.example.sns.repository.PostRepository;
import com.example.sns.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostLikeService {
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostLikeRepository postLikeRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 트랜잭션으로 묶는 이유: 알림 이벤트는 커밋 뒤에 처리되므로 커밋할 트랜잭션이 있어야 한다.
     * 묶지 않으면 이벤트가 버려지고, 좋아요 저장과 알림이 서로 다른 트랜잭션으로 갈라진다.
     */
    @Transactional
    public boolean toggleLike(Long postId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("유저를 찾을 수 없습니다."));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("게시글을 찾을 수 없습니다."));

        Optional<PostLike> existingLike = postLikeRepository.findByPostAndUser(post, user);

        if (existingLike.isPresent()) {
            postLikeRepository.delete(existingLike.get());
            eventPublisher.publishEvent(NotificationEvent.cancelled(post.getAuthor(), user, Notification.Type.POST_LIKE, post));
            return false;
        } else {
            PostLike like = new PostLike();
            like.setPost(post);
            like.setUser(user);
            postLikeRepository.save(like);

            // 게시글 작성자에게 알림
            eventPublisher.publishEvent(NotificationEvent.created(post.getAuthor(), user, Notification.Type.POST_LIKE, post));
            return true;
        }
    }

    public long countLikes(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("게시글을 찾을 수 없습니다."));
        return postLikeRepository.countByPost(post);
    }

    public boolean hasUserLikedPost(Long postId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("유저를 찾을 수 없습니다."));
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("게시글을 찾을 수 없습니다."));
        return postLikeRepository.existsByPostAndUser(post, user);
    }
}

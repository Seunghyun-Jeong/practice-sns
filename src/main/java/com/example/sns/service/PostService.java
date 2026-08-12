package com.example.sns.service;

import com.example.sns.dto.CommentDto;
import com.example.sns.dto.FeedPageDto;
import com.example.sns.dto.PostDetailDto;
import com.example.sns.dto.PostResponse;
import com.example.sns.dto.PostSummaryDto;
import com.example.sns.dto.PostUpdateRequest;
import com.example.sns.entity.Follow;
import com.example.sns.entity.Post;
import com.example.sns.entity.User;
import com.example.sns.repository.CommentLikeRepository;
import com.example.sns.repository.CommentRepository;
import com.example.sns.repository.FollowRepository;
import com.example.sns.repository.PostLikeRepository;
import com.example.sns.repository.PostRepository;
import com.example.sns.repository.UserRepository;
import com.example.sns.util.JwtUtil;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PostService {
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PostLikeRepository postLikeRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final CommentRepository commentRepository;
    private final FollowRepository followRepository;
    private final FileStorageService fileStorageService;

    public PostService(PostRepository postRepository, UserRepository userRepository, JwtUtil jwtUtil, PostLikeRepository postLikeRepository, CommentLikeRepository commentLikeRepository, CommentRepository commentRepository, FollowRepository followRepository, FileStorageService fileStorageService) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.postLikeRepository = postLikeRepository;
        this.commentLikeRepository = commentLikeRepository;
        this.commentRepository = commentRepository;
        this.followRepository = followRepository;
        this.fileStorageService = fileStorageService;
    }

    @Transactional
    public PostResponse createPost(String content, MultipartFile image, String token) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("사진을 선택해주세요.");
        }

        String username = jwtUtil.getUsernameFromToken(token);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 없음"));

        Post post = new Post();
        post.setContent(content);
        post.setImageUrl(fileStorageService.storeImage(image, "post_" + user.getId()));
        post.setAuthor(user);

        Post saved = postRepository.save(post);
        return new PostResponse(saved.getId(), saved.getContent(), saved.getImageUrl(), user.getUsername(), saved.getCreatedAt());
    }

    /** 전체 피드 한 페이지 */
    public FeedPageDto getFeedPage(Long currentUserId, int page, int size) {
        final User currentUser = currentUserId != null ? userRepository.findById(currentUserId).orElse(null) : null;
        Slice<Post> slice = postRepository.findFeed(LocalDateTime.now(), PageRequest.of(page, size));
        return new FeedPageDto(toSummaryDtos(slice.getContent(), currentUser), page, slice.hasNext());
    }

    /**
     * 내가 팔로우한 사람들의 게시글 한 페이지 (본인 글 포함).
     * 팔로우한 사람이 없으면 본인 글만 나온다.
     */
    public FeedPageDto getFollowingFeedPage(Long currentUserId, int page, int size) {
        User currentUser = currentUserId != null ? userRepository.findById(currentUserId).orElse(null) : null;
        if (currentUser == null) {
            return new FeedPageDto(List.of(), page, false);
        }

        List<User> authors = followRepository.findAllByFollower(currentUser).stream()
                .map(Follow::getFollowing)
                .collect(Collectors.toList());
        authors.add(currentUser);

        Slice<Post> slice = postRepository.findFeedByAuthors(authors, LocalDateTime.now(), PageRequest.of(page, size));
        return new FeedPageDto(toSummaryDtos(slice.getContent(), currentUser), page, slice.hasNext());
    }

    /**
     * 게시글 목록을 DTO로 변환한다.
     * 게시글마다 좋아요 수·댓글 수·좋아요 여부를 따로 조회하면 N+1이 되므로,
     * 세 가지를 각각 한 번의 쿼리로 모아서 가져온 뒤 메모리에서 붙인다.
     */
    private List<PostSummaryDto> toSummaryDtos(List<Post> posts, User currentUser) {
        if (posts.isEmpty()) {
            return List.of();
        }

        List<Long> postIds = posts.stream().map(Post::getId).collect(Collectors.toList());

        Map<Long, Long> likeCounts = toCountMap(postLikeRepository.countByPostIds(postIds));
        Map<Long, Long> commentCounts = toCountMap(commentRepository.countByPostIds(postIds));
        Set<Long> likedPostIds = currentUser == null
                ? Set.of()
                : new HashSet<>(postLikeRepository.findLikedPostIds(currentUser.getId(), postIds));

        return posts.stream()
                .map(post -> new PostSummaryDto(
                        post.getId(),
                        post.getAuthor().getUsername(),
                        post.getAuthor().getId(),
                        post.getCreatedAt().toString(),
                        post.getUpdatedAt() != null ? post.getUpdatedAt().toString() : null,
                        post.getContent(),
                        post.getImageUrl(),
                        likeCounts.getOrDefault(post.getId(), 0L),
                        commentCounts.getOrDefault(post.getId(), 0L).intValue(),
                        likedPostIds.contains(post.getId()),
                        post.getAuthor().isSuspended()
                ))
                .collect(Collectors.toList());
    }

    /** [postId, count] 결과를 Map으로 변환 */
    private Map<Long, Long> toCountMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((Long) row[0], (Long) row[1]);
        }
        return map;
    }

    public PostDetailDto getPostDetail(Long postId, Long currentUserId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글 없음"));

        long likeCount = postLikeRepository.countByPost(post);

        final User currentUser = currentUserId != null ? userRepository.findById(currentUserId).orElse(null) : null;

        List<CommentDto> commentDtos = post.getComments().stream()
                .map(comment -> {
                    long commentLikeCount = commentLikeRepository.countByComment(comment);
                    boolean commentLikedByCurrentUser = false;
                    if (currentUser != null) {
                        commentLikedByCurrentUser = commentLikeRepository.existsByCommentAndUser(comment, currentUser);
                    }
                    boolean commentAuthorSuspended = comment.getAuthor().isSuspended();
                    return new CommentDto(
                            comment.getId(),
                            commentAuthorSuspended ? null : comment.getAuthor().getUsername(),
                            commentAuthorSuspended ? null : comment.getAuthor().getId(),
                            commentAuthorSuspended ? null : comment.getAuthor().getProfileImageUrl(),
                            commentAuthorSuspended ? null : comment.getContent(),
                            comment.getCreatedAt().toString(),
                            comment.getUpdatedAt() != null ? comment.getUpdatedAt().toString() : null,
                            commentLikeCount,
                            commentLikedByCurrentUser,
                            commentAuthorSuspended
                    );
                })
                .collect(Collectors.toList());

        boolean postLikedByCurrentUser = false;
        if (currentUser != null) {
            postLikedByCurrentUser = postLikeRepository.existsByPostAndUser(post, currentUser);
        }

        boolean postAuthorSuspended = post.getAuthor().isSuspended();

        return new PostDetailDto(
                post.getId(),
                postAuthorSuspended ? null : post.getContent(),
                postAuthorSuspended ? null : post.getImageUrl(),
                postAuthorSuspended ? null : post.getAuthor().getUsername(),
                postAuthorSuspended ? null : post.getAuthor().getId(),
                postAuthorSuspended ? null : post.getAuthor().getProfileImageUrl(),
                post.getCreatedAt().toString(),
                post.getUpdatedAt() != null ? post.getUpdatedAt().toString() : null,
                likeCount,
                commentDtos.size(),
                commentDtos,
                postLikedByCurrentUser,
                postAuthorSuspended
        );
    }

    @Transactional
    public void deletePost(Long postId, String username, String role) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));

        if (!post.getAuthor().getUsername().equals(username) && !"ADMIN".equals(role)) {
            throw new SecurityException("게시글 삭제 권한이 없습니다.");
        }

        postRepository.delete(post);
    }

    @Transactional
    public void updatePost(Long postId, PostUpdateRequest request, String username) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));

        if (!post.getAuthor().getUsername().equals(username)) {
            throw new SecurityException("게시글 수정 권한이 없습니다.");
        }

        post.setContent(request.getContent());
        post.setUpdatedAt(LocalDateTime.now());
    }

    public List<PostSummaryDto> getPostsByUserIdWithExtras(Long userId, Long currentUserId) {
        final User currentUser = currentUserId != null ? userRepository.findById(currentUserId).orElse(null) : null;
        return toSummaryDtos(postRepository.findByAuthorIdWithAuthor(userId), currentUser);
    }
}
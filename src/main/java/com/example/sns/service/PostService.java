package com.example.sns.service;

import com.example.sns.dto.CommentDto;
import com.example.sns.dto.FeedPageDto;
import com.example.sns.dto.MentionDto;
import com.example.sns.dto.PostDetailDto;
import com.example.sns.dto.PostResponse;
import com.example.sns.dto.PostSummaryDto;
import com.example.sns.dto.PostUpdateRequest;
import com.example.sns.entity.Comment;
import com.example.sns.entity.Follow;
import com.example.sns.entity.Post;
import com.example.sns.entity.User;
import com.example.sns.repository.CommentLikeRepository;
import com.example.sns.repository.CommentRepository;
import com.example.sns.repository.FollowRepository;
import com.example.sns.repository.PostLikeRepository;
import com.example.sns.repository.PostRepository;
import com.example.sns.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final PostLikeRepository postLikeRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final CommentRepository commentRepository;
    private final FollowRepository followRepository;
    private final FileStorageService fileStorageService;
    private final HashtagService hashtagService;
    private final MentionService mentionService;

    public PostService(PostRepository postRepository, UserRepository userRepository, PostLikeRepository postLikeRepository, CommentLikeRepository commentLikeRepository, CommentRepository commentRepository, FollowRepository followRepository, FileStorageService fileStorageService, HashtagService hashtagService, MentionService mentionService) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.postLikeRepository = postLikeRepository;
        this.commentLikeRepository = commentLikeRepository;
        this.commentRepository = commentRepository;
        this.followRepository = followRepository;
        this.fileStorageService = fileStorageService;
        this.hashtagService = hashtagService;
        this.mentionService = mentionService;
    }

    @Transactional
    public PostResponse createPost(String content, MultipartFile image, String username) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("사진을 선택해주세요.");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 없음"));

        Post post = new Post();
        post.setContent(content);
        post.setImageUrl(fileStorageService.storeImage(image, "post_" + user.getId()));
        post.setAuthor(user);

        Post saved = postRepository.save(post);
        hashtagService.syncTags(saved);   // 본문의 #태그를 뽑아 연결
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

    /** 특정 해시태그가 달린 게시글 한 페이지 */
    public FeedPageDto getFeedPageByTag(String tag, Long currentUserId, int page, int size) {
        final User currentUser = currentUserId != null ? userRepository.findById(currentUserId).orElse(null) : null;
        String normalized = tag == null ? "" : tag.trim().toLowerCase();

        Slice<Post> slice = postRepository.findFeedByTag(normalized, LocalDateTime.now(), PageRequest.of(page, size));
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

        List<Comment> comments = post.getComments();
        List<CommentDto> commentDtos = buildCommentTree(comments, currentUser);

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
                comments.size(),      // 답글도 댓글 수에 포함한다
                commentDtos,
                postLikedByCurrentUser,
                postAuthorSuspended
        );
    }

    /**
     * 원 댓글 아래에 답글을 붙여서 돌려준다.
     *
     * 좋아요 수와 내가 눌렀는지 여부는 댓글마다 조회하지 않고 두 번의 쿼리로 모아서 가져온다.
     * 예전에 피드에서 게시글마다 조회하다가 쿼리가 59번 나갔던 것과 같은 모양인데,
     * 답글이 생기면서 댓글 수가 늘어난 만큼 이쪽도 그대로 두면 더 커진다.
     */
    private List<CommentDto> buildCommentTree(List<Comment> comments, User currentUser) {
        if (comments.isEmpty()) {
            return List.of();
        }

        List<Long> commentIds = comments.stream().map(Comment::getId).toList();
        Map<Long, Long> likeCounts = toCountMap(commentLikeRepository.countByCommentIds(commentIds));
        Set<Long> likedByMe = currentUser == null
                ? Set.of()
                : new HashSet<>(commentLikeRepository.findLikedCommentIds(currentUser.getId(), commentIds));

        // 멘션도 댓글마다 조회하면 같은 문제가 반복되므로, 모든 댓글의 @아이디를 모아 한 번에 찾는다
        Map<String, MentionDto> mentionsByName = resolveMentions(comments);

        Map<Long, CommentDto> dtoById = new LinkedHashMap<>();
        for (Comment comment : comments) {
            dtoById.put(comment.getId(), toCommentDto(comment, likeCounts, likedByMe, mentionsByName));
        }

        List<CommentDto> roots = new ArrayList<>();
        for (Comment comment : comments) {
            CommentDto dto = dtoById.get(comment.getId());
            if (!comment.isReply()) {
                roots.add(dto);
                continue;
            }

            CommentDto parentDto = dtoById.get(comment.getParent().getId());
            if (parentDto != null) {
                parentDto.getReplies().add(dto);
            } else {
                // 부모가 목록에 없는 경우는 없지만, 있더라도 답글이 사라지지는 않게 둔다
                roots.add(dto);
            }
        }
        return roots;
    }

    /** 댓글 전체에 적힌 @아이디를 한 번에 조회해 아이디로 찾을 수 있게 만든다 */
    private Map<String, MentionDto> resolveMentions(List<Comment> comments) {
        Set<String> names = new HashSet<>();
        for (Comment comment : comments) {
            if (!comment.getAuthor().isSuspended()) {
                names.addAll(mentionService.extractUsernames(comment.getContent()));
            }
        }
        if (names.isEmpty()) {
            return Map.of();
        }

        return mentionService.findByUsernames(names).stream()
                .collect(Collectors.toMap(
                        User::getUsername,
                        user -> new MentionDto(user.getUsername(), user.getId())));
    }

    private CommentDto toCommentDto(Comment comment, Map<Long, Long> likeCounts, Set<Long> likedByMe,
                                    Map<String, MentionDto> mentionsByName) {
        boolean suspended = comment.getAuthor().isSuspended();

        CommentDto dto = new CommentDto();
        dto.setId(comment.getId());
        dto.setAuthor(suspended ? null : comment.getAuthor().getUsername());
        dto.setAuthorId(suspended ? null : comment.getAuthor().getId());
        dto.setAuthorProfileImageUrl(suspended ? null : comment.getAuthor().getProfileImageUrl());
        dto.setContent(suspended ? null : comment.getContent());
        dto.setCreatedAt(comment.getCreatedAt().toString());
        dto.setUpdatedAt(comment.getUpdatedAt() != null ? comment.getUpdatedAt().toString() : null);
        dto.setLikeCount(likeCounts.getOrDefault(comment.getId(), 0L));
        dto.setLikedByCurrentUser(likedByMe.contains(comment.getId()));
        dto.setSuspended(suspended);
        dto.setParentId(comment.isReply() ? comment.getParent().getId() : null);

        // 정지된 사람의 댓글은 내용을 가리므로 멘션도 내려주지 않는다.
        // 없는 아이디를 적었으면 여기서 걸러져 링크가 생기지 않는다.
        if (!suspended) {
            dto.setMentions(mentionService.extractUsernames(comment.getContent()).stream()
                    .map(mentionsByName::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
        }
        return dto;
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
        hashtagService.syncTags(post);   // 본문이 바뀌었으니 태그도 다시 계산
    }

    public List<PostSummaryDto> getPostsByUserIdWithExtras(Long userId, Long currentUserId) {
        final User currentUser = currentUserId != null ? userRepository.findById(currentUserId).orElse(null) : null;
        return toSummaryDtos(postRepository.findByAuthorIdWithAuthor(userId), currentUser);
    }
}
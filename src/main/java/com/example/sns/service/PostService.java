package com.example.sns.service;

import com.example.sns.dto.CommentDto;
import com.example.sns.dto.CommentPageDto;
import com.example.sns.dto.FeedCursor;
import com.example.sns.dto.FeedPageDto;
import com.example.sns.dto.FeedQuery;
import com.example.sns.dto.MentionDto;
import com.example.sns.dto.PostDetailDto;
import com.example.sns.dto.PostResponse;
import com.example.sns.dto.PostSummaryDto;
import com.example.sns.dto.PostUpdateRequest;
import com.example.sns.entity.Comment;
import com.example.sns.entity.Post;
import com.example.sns.entity.User;
import com.example.sns.exception.ForbiddenException;
import com.example.sns.exception.NotFoundException;
import com.example.sns.repository.CommentLikeRepository;
import com.example.sns.repository.CommentRepository;
import com.example.sns.repository.PostLikeRepository;
import com.example.sns.repository.PostRepository;
import com.example.sns.repository.UserRepository;
import com.example.sns.util.TextContent;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
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
    /** 피드에서 한 번에 불러오는 게시글 수 */
    private static final int FEED_PAGE_SIZE = 10;

    /** 프로필에서 한 번에 불러오는 게시글 수. 한 줄에 3장씩 놓이므로 4줄이 꽉 차게 한다 */
    private static final int PROFILE_PAGE_SIZE = 12;

    /** 게시글 상세에서 댓글을 한 번에 불러오는 개수 (원 댓글, 답글 모두) */
    private static final int COMMENT_PAGE_SIZE = 20;

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostLikeRepository postLikeRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final CommentRepository commentRepository;
    private final FileStorageService fileStorageService;
    private final HashtagService hashtagService;
    private final MentionService mentionService;
    private final LatestFeedPost latestFeedPost;

    public PostService(PostRepository postRepository, UserRepository userRepository, PostLikeRepository postLikeRepository, CommentLikeRepository commentLikeRepository, CommentRepository commentRepository, FileStorageService fileStorageService, HashtagService hashtagService, MentionService mentionService, LatestFeedPost latestFeedPost) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.postLikeRepository = postLikeRepository;
        this.commentLikeRepository = commentLikeRepository;
        this.commentRepository = commentRepository;
        this.fileStorageService = fileStorageService;
        this.hashtagService = hashtagService;
        this.mentionService = mentionService;
        this.latestFeedPost = latestFeedPost;
    }

    @Transactional
    public PostResponse createPost(String content, MultipartFile image, String username) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("사진을 선택해주세요.");
        }
        String text = TextContent.require(content, Post.MAX_CONTENT_LENGTH);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("유저를 찾을 수 없습니다."));

        Post post = new Post();
        post.setContent(text);
        post.setImageUrl(fileStorageService.storeImage(image, "post_" + user.getId()));
        post.setAuthor(user);

        Post saved = postRepository.save(post);
        hashtagService.syncTags(saved);   // 본문의 #태그를 뽑아 연결
        latestFeedPost.evict();            // 새 글 확인이 기억해 둔 옛 값으로 답하지 않게
        return new PostResponse(saved.getId(), saved.getContent(), saved.getImageUrl(), user.getUsername(), saved.getCreatedAt());
    }

    /**
     * 피드 한 묶음. beforeAt, beforeId가 없으면 첫 묶음이고,
     * 있으면 그 글보다 먼저 쓴 글부터 이어서 준다.
     */
    public FeedPageDto getFeed(FeedQuery query, LocalDateTime beforeAt, Long beforeId) {
        LocalDateTime now = LocalDateTime.now();
        PageRequest page = PageRequest.of(0, query.kind() == FeedQuery.Kind.AUTHOR ? PROFILE_PAGE_SIZE : FEED_PAGE_SIZE);
        Slice<Post> slice = switch (query.kind()) {
            case ALL -> beforeAt == null
                    ? postRepository.findFeed(now, page)
                    : postRepository.findFeedBefore(now, beforeAt, beforeId, page);
            // 내가 팔로우한 사람들의 글과 내 글. 팔로우한 사람이 없으면 내 글만 나온다
            case FOLLOWING -> beforeAt == null
                    ? postRepository.findFollowingFeed(query.userId(), now, page)
                    : postRepository.findFollowingFeedBefore(query.userId(), now, beforeAt, beforeId, page);
            case TAG -> beforeAt == null
                    ? postRepository.findFeedByTag(query.tag(), now, page)
                    : postRepository.findFeedByTagBefore(query.tag(), now, beforeAt, beforeId, page);
            case AUTHOR -> beforeAt == null
                    ? postRepository.findAuthorFeed(query.authorId(), now, page)
                    : postRepository.findAuthorFeedBefore(query.authorId(), now, beforeAt, beforeId, page);
        };
        return new FeedPageDto(toSummaryDtos(slice.getContent(), query.userId()), slice.hasNext());
    }

    /**
     * 화면 맨 위 글(afterAt, afterId)보다 나중에 쓴 글이 이 피드에 있는지.
     * 화면이 비어 있으면(afterAt 없음) 보여줄 글이 하나라도 있는지를 본다.
     * 전체 피드는 누구에게나 답이 같아서 가장 최근 글을 잠깐 기억해 두고 비교만 한다.
     */
    public boolean hasNewPosts(FeedQuery query, LocalDateTime afterAt, Long afterId) {
        LocalDateTime now = LocalDateTime.now();
        return switch (query.kind()) {
            case ALL -> {
                FeedCursor latest = latestFeedPost.get(this::loadLatestFeedPost);
                yield latest != null && latest.isAfter(afterAt, afterId);
            }
            case FOLLOWING -> afterAt == null
                    ? postRepository.findFollowingFeed(query.userId(), now, onePost()).hasContent()
                    : !postRepository.findFollowingFeedAfter(query.userId(), now, afterAt, afterId, onePost()).isEmpty();
            case TAG -> afterAt == null
                    ? postRepository.findFeedByTag(query.tag(), now, onePost()).hasContent()
                    : !postRepository.findFeedByTagAfter(query.tag(), now, afterAt, afterId, onePost()).isEmpty();
            // 새 글 확인은 메인 피드에만 있다. 프로필 조건으로 묻는 경로는 없다
            case AUTHOR -> throw new IllegalArgumentException("프로필은 새 글 확인을 하지 않습니다.");
        };
    }

    private FeedCursor loadLatestFeedPost() {
        return postRepository.findFeed(LocalDateTime.now(), onePost()).getContent().stream()
                .findFirst()
                .map(post -> new FeedCursor(post.getCreatedAt(), post.getId()))
                .orElse(null);
    }

    private PageRequest onePost() {
        return PageRequest.of(0, 1);
    }

    /**
     * 게시글 목록을 DTO로 변환한다.
     * 게시글마다 좋아요 수·댓글 수·좋아요 여부를 따로 조회하면 N+1이 되므로,
     * 세 가지를 각각 한 번의 쿼리로 모아서 가져온 뒤 메모리에서 붙인다.
     */
    private List<PostSummaryDto> toSummaryDtos(List<Post> posts, Long currentUserId) {
        if (posts.isEmpty()) {
            return List.of();
        }

        List<Long> postIds = posts.stream().map(Post::getId).collect(Collectors.toList());

        Map<Long, Long> likeCounts = toCountMap(postLikeRepository.countByPostIds(postIds));
        Map<Long, Long> commentCounts = toCountMap(commentRepository.countByPostIds(postIds));
        Set<Long> likedPostIds = currentUserId == null
                ? Set.of()
                : new HashSet<>(postLikeRepository.findLikedPostIds(currentUserId, postIds));

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
        Post post = postRepository.findByIdWithAuthor(postId)
                .orElseThrow(() -> new NotFoundException("게시글을 찾을 수 없습니다."));

        long likeCount = postLikeRepository.countByPost(post);

        final User currentUser = currentUserId != null ? userRepository.findById(currentUserId).orElse(null) : null;

        // 댓글은 전부 읽지 않고 원 댓글 첫 묶음만 담는다. 나머지는 스크롤하면서 getRootComments로 이어 받는다
        CommentPageDto firstComments = getRootComments(postId, currentUserId, null, null);

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
                commentRepository.countByPostId(postId),   // 답글도 댓글 수에 포함한다
                firstComments.getComments(),
                firstComments.isHasNext(),
                postLikedByCurrentUser,
                postAuthorSuspended
        );
    }

    /**
     * 원 댓글 한 묶음 (최신순). beforeAt, beforeId가 없으면 첫 묶음이고,
     * 있으면 그 댓글보다 먼저 쓴 것부터 이어서 준다.
     */
    public CommentPageDto getRootComments(Long postId, Long currentUserId, LocalDateTime beforeAt, Long beforeId) {
        Slice<Comment> slice = beforeAt == null
                ? commentRepository.findRootComments(postId, commentPage())
                : commentRepository.findRootCommentsBefore(postId, beforeAt, beforeId, commentPage());
        return new CommentPageDto(toCommentDtos(slice.getContent(), currentUserId), slice.hasNext());
    }

    /**
     * 한 원 댓글의 답글 한 묶음 (오래된 순). afterAt, afterId가 없으면 첫 묶음이고,
     * 있으면 그 답글보다 나중에 쓴 것부터 이어서 준다.
     */
    public CommentPageDto getReplies(Long postId, Long parentId, Long currentUserId,
                                     LocalDateTime afterAt, Long afterId) {
        Slice<Comment> slice = afterAt == null
                ? commentRepository.findReplies(postId, parentId, commentPage())
                : commentRepository.findRepliesAfter(postId, parentId, afterAt, afterId, commentPage());
        return new CommentPageDto(toCommentDtos(slice.getContent(), currentUserId), slice.hasNext());
    }

    /** 댓글 하나. 방금 쓴 댓글을 모달을 다시 열지 않고 목록에 붙일 때 쓴다 */
    public CommentDto getComment(Long postId, Long commentId, Long currentUserId) {
        Comment comment = commentRepository.findById(commentId)
                .filter(c -> c.getPost().getId().equals(postId))
                .orElseThrow(() -> new NotFoundException("댓글을 찾을 수 없습니다."));
        return toCommentDtos(List.of(comment), currentUserId).get(0);
    }

    private PageRequest commentPage() {
        return PageRequest.of(0, COMMENT_PAGE_SIZE);
    }

    /**
     * 댓글 묶음을 화면에 줄 형태로 바꾼다.
     *
     * 좋아요 수, 내가 눌렀는지 여부, 답글 수, 멘션 대상은 댓글마다 조회하지 않고 묶음의 id로 한 번씩만 조회한다.
     * 예전에 피드에서 게시글마다 조회하다가 쿼리가 59번 나갔던 것과 같은 모양이 되지 않게 하기 위해서다.
     */
    private List<CommentDto> toCommentDtos(List<Comment> comments, Long currentUserId) {
        if (comments.isEmpty()) {
            return List.of();
        }

        List<Long> commentIds = comments.stream().map(Comment::getId).toList();
        Map<Long, Long> likeCounts = toCountMap(commentLikeRepository.countByCommentIds(commentIds));
        Set<Long> likedByMe = currentUserId == null
                ? Set.of()
                : new HashSet<>(commentLikeRepository.findLikedCommentIds(currentUserId, commentIds));

        // 답글 수는 원 댓글에만 있다. 답글 묶음이면 조회하지 않는다
        List<Long> rootIds = comments.stream().filter(c -> !c.isReply()).map(Comment::getId).toList();
        Map<Long, Long> replyCounts = rootIds.isEmpty()
                ? Map.of()
                : toCountMap(commentRepository.countRepliesByParentIds(rootIds));

        // 멘션도 댓글마다 조회하면 같은 문제가 반복되므로, 모든 댓글의 @아이디를 모아 한 번에 찾는다
        Map<String, MentionDto> mentionsByName = resolveMentions(comments);

        return comments.stream()
                .map(comment -> toCommentDto(comment, likeCounts, likedByMe, replyCounts, mentionsByName))
                .toList();
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
                                    Map<Long, Long> replyCounts, Map<String, MentionDto> mentionsByName) {
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
        dto.setReplyCount(replyCounts.getOrDefault(comment.getId(), 0L));

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
                .orElseThrow(() -> new NotFoundException("게시글을 찾을 수 없습니다."));

        if (!post.getAuthor().getUsername().equals(username) && !"ADMIN".equals(role)) {
            throw new ForbiddenException("게시글 삭제 권한이 없습니다.");
        }

        postRepository.delete(post);
    }

    @Transactional
    public void updatePost(Long postId, PostUpdateRequest request, String username) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("게시글을 찾을 수 없습니다."));

        if (!post.getAuthor().getUsername().equals(username)) {
            throw new ForbiddenException("게시글 수정 권한이 없습니다.");
        }

        post.setContent(TextContent.require(request.getContent(), Post.MAX_CONTENT_LENGTH));
        post.setUpdatedAt(LocalDateTime.now());
        hashtagService.syncTags(post);   // 본문이 바뀌었으니 태그도 다시 계산
    }

    /** 프로필의 게시물 수 */
    public long countPosts(Long userId) {
        return postRepository.countByAuthor_Id(userId);
    }

    /** 한 사람의 글 전부. 관리자 화면(사용자별 작성 글)만 쓴다. 프로필은 getFeed로 끊어 읽는다 */
    public List<PostSummaryDto> getPostsByUserIdWithExtras(Long userId, Long currentUserId) {
        return toSummaryDtos(postRepository.findByAuthorIdWithAuthor(userId), currentUserId);
    }
}
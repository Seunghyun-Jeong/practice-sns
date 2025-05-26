package com.example.sns.service;

import com.example.sns.dto.CommentDto;
import com.example.sns.dto.PostDetailDto;
import com.example.sns.dto.PostRequest;
import com.example.sns.dto.PostResponse;
import com.example.sns.dto.PostSummaryDto;
import com.example.sns.dto.PostUpdateRequest;
import com.example.sns.entity.Post;
import com.example.sns.entity.User;
import com.example.sns.repository.PostRepository;
import com.example.sns.repository.UserRepository;
import com.example.sns.util.JwtUtil;
import java.nio.file.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostService {
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    public PostService(PostRepository postRepository, UserRepository userRepository, JwtUtil jwtUtil) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
    }

    public PostResponse createPost(PostRequest postRequest, String token) {
        String username = jwtUtil.getUsernameFromToken(token);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 없음"));

        Post post = new Post();
        post.setTitle(postRequest.getTitle());
        post.setContent(postRequest.getContent());
        post.setAuthor(user);

        Post saved = postRepository.save(post);
        return new PostResponse(saved.getId(), saved.getTitle(), saved.getContent(), user.getUsername(), saved.getCreatedAt());
    }

    public List<PostResponse> getAllPosts() {
        return postRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(post -> new PostResponse(
                        post.getId(),
                        post.getTitle(),
                        post.getContent(),
                        post.getAuthor().getUsername(),
                        post.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    public List<PostSummaryDto> getPostsummaries() {
        return postRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(post -> new PostSummaryDto(
                        post.getId(),
                        post.getTitle(),
                        post.getAuthor().getUsername(),
                        post.getCreatedAt().toString(),
                        post.getUpdatedAt() != null ? post.getUpdatedAt().toString() : null,                        post.getContent()
                ))
                .collect(Collectors.toList());
    }

    public PostDetailDto getPostDetail(Long id) {
        Post post = postRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("게시글 없음"));
        List<CommentDto> commentDtos = post.getComments().stream()
                .map(comment -> new CommentDto(
                        comment.getId(),
                        comment.getAuthor().getUsername(),
                        comment.getContent(),
                        comment.getCreatedAt().toString()
                ))
                .collect(Collectors.toList());

        return new PostDetailDto(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getAuthor().getUsername(),
                post.getAuthor().getId(),
                post.getCreatedAt().toString(),
                post.getUpdatedAt() != null ? post.getUpdatedAt().toString() : null,
                post.getLikes().size(),
                commentDtos.size(),
                commentDtos
        );
    }

    @Transactional
    public void deletePost(Long postId, String username) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));

        if (!post.getAuthor().getUsername().equals(username)) {
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

        post.setTitle(request.getTitle());
        post.setContent(request.getContent());
        post.setUpdatedAt(LocalDateTime.now());
    }
}

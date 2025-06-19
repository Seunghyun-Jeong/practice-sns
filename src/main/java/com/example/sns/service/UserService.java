package com.example.sns.service;

import com.example.sns.dto.UserProfileDto;
import com.example.sns.dto.UserSignUpRequest;
import com.example.sns.entity.User;
import com.example.sns.entity.User.Role;
import com.example.sns.repository.CommentLikeRepository;
import com.example.sns.repository.CommentRepository;
import com.example.sns.repository.PostLikeRepository;
import com.example.sns.repository.PostRepository;
import com.example.sns.repository.UserRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;
    private final CommentLikeRepository commentLikeRepository;

    public void signup(UserSignUpRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("이미 존재하는 ID입니다.");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(User.Role.USER)
                .build();

        userRepository.save(user);
    }

    @Transactional
    public void deleteUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        userRepository.delete(user);
    }

    @Transactional
    public void suspendUser(Long userId, String duration) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 사용자를 찾을 수 없습니다."));

        if (user.getRole() == User.Role.ADMIN) {
            throw new IllegalArgumentException("관리자는 정지할 수 없습니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime until;

        switch (duration) {
            case "1d" -> until = now.plusDays(1);
            case "3d" -> until = now.plusDays(3);
            case "7d" -> until = now.plusDays(7);
            case "30d" -> until = now.plusDays(30);
            case "90d" -> until = now.plusDays(90);
            case "forever" -> until = LocalDateTime.of(9999, 12, 31, 0, 0);
            default -> throw new IllegalArgumentException("잘못된 정지 기간입니다.");
        }

        user.setSuspendedUntil(until);

        postRepository.deleteAll(user.getPosts());
        commentRepository.deleteAll(user.getComments());
        postLikeRepository.deleteAll(user.getPostLikes());
        commentLikeRepository.deleteAll(user.getCommentLikes());

        userRepository.save(user);
    }

    public UserProfileDto getProfileById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 사용자가 존재하지 않습니다."));

        return new UserProfileDto(
                user.getUsername(),
                user.getProfileImageUrl()
        );
    }

    @Transactional
    public void updateUsername(Long userId, String newUsername) {
        if (userRepository.existsByUsername(newUsername)) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        user.updateUsername(newUsername);
    }
}

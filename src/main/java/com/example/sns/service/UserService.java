package com.example.sns.service;

import com.example.sns.dto.SuspendedUserDto;
import com.example.sns.dto.UserProfileDto;
import com.example.sns.dto.UserSignUpRequest;
import com.example.sns.entity.User;
import com.example.sns.entity.User.Role;
import com.example.sns.repository.UserRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${file.upload-dir}")
    private String uploadDir;

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

        userRepository.save(user);
    }

    public UserProfileDto getProfileById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 사용자가 존재하지 않습니다."));

        return new UserProfileDto(
                user.getUsername(),
                user.getProfileImageUrl(),
                user.isSuspended()
        );
    }

    public List<SuspendedUserDto> getSuspendedUsers() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
        return userRepository.findBySuspendedUntilAfterOrderBySuspendedUntilAsc(LocalDateTime.now()).stream()
                .map(user -> new SuspendedUserDto(
                        user.getId(),
                        user.getUsername(),
                        user.getSuspendedUntil().getYear() >= 9999
                                ? "영구 정지"
                                : user.getSuspendedUntil().format(formatter)
                ))
                .collect(Collectors.toList());
    }

    @Transactional
    public void unsuspendUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 사용자를 찾을 수 없습니다."));

        user.setSuspendedUntil(null);
    }

    @Transactional
    public String updateProfileImage(String username, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("이미지 파일을 선택해주세요.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        String original = file.getOriginalFilename();
        String ext = (original != null && original.contains("."))
                ? original.substring(original.lastIndexOf('.'))
                : "";
        String filename = "profile_" + user.getId() + "_" + System.currentTimeMillis() + ext;

        try {
            Path dir = Paths.get(uploadDir).toAbsolutePath();
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), dir.resolve(filename));
        } catch (IOException e) {
            throw new RuntimeException("이미지 저장에 실패했습니다.", e);
        }

        String url = "/uploads/" + filename;
        user.setProfileImageUrl(url);
        return url;
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

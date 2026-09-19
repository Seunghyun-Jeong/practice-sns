package com.example.sns.controller;

import com.example.sns.config.AuthTokenIssuer;
import com.example.sns.config.MyUserDetails;
import com.example.sns.dto.UserLoginRequest;
import com.example.sns.dto.UserSignUpRequest;
import com.example.sns.dto.UserUpdateRequestDto;
import com.example.sns.entity.User;
import com.example.sns.exception.NotFoundException;
import com.example.sns.repository.UserRepository;
import com.example.sns.service.RefreshTokenService;
import com.example.sns.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenIssuer authTokenIssuer;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/signup")
    public ResponseEntity<Map<String, String>> signUp(@ModelAttribute @Valid UserSignUpRequest request) {
        userService.signup(request);

        return ResponseEntity.ok(Map.of(
                "message", "회원가입이 완료되었습니다.",
                "redirectUrl", "/login"
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody UserLoginRequest request, HttpServletResponse response) {
        Optional<User> userOpt = userRepository.findByUsername(request.getUsername());

        if (userOpt.isEmpty() || !passwordEncoder.matches(request.getPassword(), userOpt.get().getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "아이디 또는 비밀번호를 잘못 입력하셨습니다."));
        }
        User user = userOpt.get();

        if (user.isSuspended()) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
            return  ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "로그인 하려는 계정은 현재 정지되었습니다. \n이용 정지 종료: " + user.getSuspendedUntil().format(formatter)));
        }

        authTokenIssuer.issueLogin(user, response);

        return ResponseEntity.ok(Map.of(
                "message", "로그인 성공"
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        // 쿠키만 지우면 브라우저에서만 사라지고 리프레시 토큰은 서버에 남는다.
        // 남아 있으면 그 값을 가진 쪽은 계속 재발급을 받을 수 있으므로 DB에서도 지운다.
        // 지우는 것은 이 기기의 것 하나뿐이라 다른 기기의 로그인은 유지된다.
        refreshTokenService.revoke(AuthTokenIssuer.readCookie(request, AuthTokenIssuer.REFRESH_COOKIE));
        authTokenIssuer.clear(response);

        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Map<String, String>> deleteCurrentUser(@AuthenticationPrincipal MyUserDetails user, HttpServletResponse response) {
        // 남아 있던 다른 기기의 리프레시 토큰까지 User의 cascade로 함께 지워진다
        userService.deleteUser(user.getUsername());
        authTokenIssuer.clear(response);

        return ResponseEntity.ok(Map.of("message", "회원 탈퇴가 완료되었습니다."));
    }

    @PatchMapping("/suspend/{userId}")
    public ResponseEntity<Map<String, String>> suspendUser(@PathVariable Long userId, @RequestParam String duration) {
        // 관리자 권한 검사는 SecurityConfig의 hasAuthority("ADMIN")가 담당한다.
        userService.suspendUser(userId, duration);

        return ResponseEntity.ok(Map.of("message", "사용자가 이용 정지가 되었습니다."));
    }

    @PatchMapping("/unsuspend/{userId}")
    public ResponseEntity<Map<String, String>> unsuspendUser(@PathVariable Long userId) {
        // 관리자 권한 검사는 SecurityConfig의 hasAuthority("ADMIN")가 담당한다.
        userService.unsuspendUser(userId);

        return ResponseEntity.ok(Map.of("message", "이용 정지가 해제되었습니다."));
    }

    @PutMapping("/username")
    public ResponseEntity<Map<String, String>> updateUsername(@Valid @RequestBody UserUpdateRequestDto requestDto, @AuthenticationPrincipal MyUserDetails user, HttpServletResponse response) {
        String newUsername = requestDto.getUsername();

        if (newUsername == null || newUsername.trim().isEmpty()) {
            throw new IllegalArgumentException("새 닉네임을 입력해주세요.");
        }

        userService.updateUsername(user.getUserId(), newUsername);

        // 쿠키의 토큰에 예전 닉네임이 남으면 인증이 깨지므로 새로 발급한다.
        // 리프레시 토큰은 닉네임이 아니라 유저를 가리키고 있어 그대로 둬도 된다.
        User updated = userRepository.findById(user.getUserId())
                .orElseThrow(() -> new NotFoundException("유저를 찾을 수 없습니다."));
        authTokenIssuer.writeAccessToken(updated, response);

        return ResponseEntity.ok(Map.of("message", "닉네임이 수정되었습니다."));
    }

    @PostMapping("/profile-image")
    public ResponseEntity<Map<String, String>> updateProfileImage(
            @RequestParam("profileImage") MultipartFile file,
            @AuthenticationPrincipal MyUserDetails user) {
        String imageUrl = userService.updateProfileImage(user.getUsername(), file);

        return ResponseEntity.ok(Map.of(
                "message", "프로필 이미지가 변경되었습니다.",
                "imageUrl", imageUrl
        ));
    }
}

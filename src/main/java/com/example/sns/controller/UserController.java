package com.example.sns.controller;

import com.example.sns.dto.UserLoginRequest;
import com.example.sns.dto.UserSignUpRequest;
import com.example.sns.dto.UserUpdateRequestDto;
import com.example.sns.entity.User;
import com.example.sns.entity.User.Role;
import com.example.sns.repository.UserRepository;
import com.example.sns.service.UserService;
import com.example.sns.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.swing.text.html.Option;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CookieValue;
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

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @PostMapping("/signup")
    public ResponseEntity<Map<String, String>> signUp(@ModelAttribute @Valid UserSignUpRequest request) {
        Map<String, String> response = new HashMap<>();

        try {
            userService.signup(request);
            response.put("message", "회원가입이 완료되었습니다.");
            response.put("redirectUrl", "/login");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody UserLoginRequest request, HttpServletResponse response) {
        Optional<User> userOpt = userRepository.findByUsername(request.getUsername());

        if (userOpt.isEmpty() || !passwordEncoder.matches(request.getPassword(), userOpt.get().getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "아이디 또는 비밀전호가 잘못되었습니다."));
        }
        User user = userOpt.get();

        if (user.getSuspendedUntil() != null && user.getSuspendedUntil().isAfter(LocalDateTime.now())) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
            return  ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "로그인 하려는 계정은 현재 정지되었습니다. \n이용 정지 종료: " + user.getSuspendedUntil().format(formatter)));
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole().name());

        Cookie cookie = new Cookie("JWT_TOKEN", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60 * 24);

        response.addCookie(cookie);

        return ResponseEntity.ok(Map.of(
                "message", "로그인 성공"
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("JWT_TOKEN", "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .build();

        response.addHeader("Set-Cookie", cookie.toString());

        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Map<String, String>> deleteCurrentUser(@CookieValue(value = "JWT_TOKEN", required = false) String token, HttpServletResponse response) {
        Map<String, String> res = new HashMap<>();

        if (token == null || !jwtUtil.validateToken(token)) {
            res.put("message", "유효하지 않은 토큰입니다");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(res);
        }

        String username = jwtUtil.getUsernameFromToken(token);
        userService.deleteUser(username);

        ResponseCookie expiredCookie = ResponseCookie.from("JWT_TOKEN", "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader("Set-Cookie", expiredCookie.toString());

        res.put("message", "회원 탈퇴가 완료되었습니다.");
        return ResponseEntity.ok(res);
    }

    @PatchMapping("/suspend/{userId}")
    public ResponseEntity<Map<String, String>> suspendUser(@PathVariable Long userId, @RequestParam String duration, @CookieValue(value = "JWT_TOKEN", required = false) String token) {
        Map<String, String> res = new HashMap<>();

        if (token == null || !jwtUtil.validateToken(token)) {
            res.put("message", "유효하지 않은 토큰입니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(res);
        }

        String username = jwtUtil.getUsernameFromToken(token);
        Optional<User> adminOpt = userRepository.findByUsername(username);

        if (adminOpt.isEmpty() || adminOpt.get().getRole() != Role.ADMIN) {
            res.put("message", "권한이 없습니다.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(res);
        }

        try {
            userService.suspendUser(userId, duration);
            res.put("message", "사용자가 이용 정지가 되었습니다.");
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException e) {
            res.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(res);
        }
    }

    @PutMapping("/username")
    public ResponseEntity<Map<String, String>> updateUsername(@Valid @RequestBody UserUpdateRequestDto requestDto, @CookieValue(value = "JWT_TOKEN", required = false) String token, HttpServletResponse response) {
        Map<String, String> res = new HashMap<>();

        if (token == null || !jwtUtil.validateToken(token)) {
            res.put("message", "유효하지 않은 토큰입니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(res);
        }

        String username = jwtUtil.getUsernameFromToken(token);
        Optional<User> userOpt = userRepository.findByUsername(username);

        if (userOpt.isEmpty()) {
            res.put("message", "사용자를 찾을 수 없습니다.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(res);
        }

        String newUsername = requestDto.getUsername();

        if (newUsername == null || newUsername.trim().isEmpty()) {
            res.put("message", "새 닉네임을 입력해주세요.");
            return ResponseEntity.badRequest().body(res);
        }

        try {
            userService.updateUsername(userOpt.get().getId(), newUsername);

            String newToken = jwtUtil.generateToken(userOpt.get().getId(), newUsername, userOpt.get().getRole().name());

            Cookie cookie = new Cookie("JWT_TOKEN", newToken);
            cookie.setHttpOnly(true);
            cookie.setSecure(false);
            cookie.setPath("/");
            cookie.setMaxAge(60 * 60 * 24);

            response.addCookie(cookie);

            res.put("message", "닉네임이 수정되었습니다.");
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException e) {
            res.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(res);
        }
    }
}

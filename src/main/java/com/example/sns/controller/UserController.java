package com.example.sns.controller;

import com.example.sns.dto.UserSignUpRequest;
import com.example.sns.service.UserService;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;


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
}

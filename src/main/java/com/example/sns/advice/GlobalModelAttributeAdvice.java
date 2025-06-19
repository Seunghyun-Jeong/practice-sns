package com.example.sns.advice;

import com.example.sns.service.UserService;
import com.example.sns.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAttributeAdvice {
    private final JwtUtil jwtUtil;

    @ModelAttribute
    public void addGlobalAttributes(Model model,
                                    @CookieValue(value = "JWT_TOKEN", required = false) String token) {
        if (token != null && jwtUtil.validateToken(token)) {
            String username = jwtUtil.getUsernameFromToken(token);
            Long userId = jwtUtil.getUserIdFromToken(token);

            model.addAttribute("username", username);
            model.addAttribute("userId", userId);
        }
    }
}

package com.example.sns.advice;

import com.example.sns.config.MyUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** 모든 뷰에서 쓰는 로그인 사용자 정보(헤더의 닉네임 등)를 모델에 실어준다. */
@ControllerAdvice
public class GlobalModelAttributeAdvice {

    @ModelAttribute
    public void addGlobalAttributes(Model model, @AuthenticationPrincipal MyUserDetails user) {
        if (user != null) {
            model.addAttribute("username", user.getUsername());
            model.addAttribute("currentUsername", user.getUsername());
            model.addAttribute("userId", user.getUserId());
            model.addAttribute("userRole", user.getRole());
        }
    }
}

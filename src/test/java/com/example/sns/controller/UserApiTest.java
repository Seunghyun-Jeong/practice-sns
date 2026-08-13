package com.example.sns.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sns.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원가입 / 로그인 API 테스트.
 * 아이디 길이 제약이 DB와 어긋나 500이 나던 문제가 있었기 때문에,
 * 검증에 걸리는 값이 실제로 400과 안내 메시지로 돌아오는지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("정상적인 값이면 회원가입에 성공한다")
    void 회원가입_성공() throws Exception {
        mockMvc.perform(post("/api/users/signup")
                        .param("username", "newuser")
                        .param("password", "Test1234!"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("회원가입이 완료되었습니다."));
    }

    @Test
    @DisplayName("아이디가 10자를 넘으면 500이 아니라 400과 안내 문구를 준다")
    void 아이디가_길면_400() throws Exception {
        mockMvc.perform(post("/api/users/signup")
                        .param("username", "abcdefghijk")   // 11자
                        .param("password", "Test1234!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("아이디는 4자 이상 10자 이하여야 합니다."));
    }

    @Test
    @DisplayName("아이디가 4자 미만이면 400을 준다")
    void 아이디가_짧으면_400() throws Exception {
        mockMvc.perform(post("/api/users/signup")
                        .param("username", "ab")
                        .param("password", "Test1234!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("아이디는 4자 이상 10자 이하여야 합니다."));
    }

    @Test
    @DisplayName("아이디에 대문자가 들어가면 400을 준다")
    void 대문자_아이디는_400() throws Exception {
        mockMvc.perform(post("/api/users/signup")
                        .param("username", "AbcTest")
                        .param("password", "Test1234!"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("비밀번호가 규칙에 맞지 않으면 400을 준다")
    void 약한_비밀번호는_400() throws Exception {
        mockMvc.perform(post("/api/users/signup")
                        .param("username", "okuser")
                        .param("password", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("이미 있는 아이디로는 가입할 수 없다")
    void 중복_아이디는_400() throws Exception {
        mockMvc.perform(post("/api/users/signup")
                .param("username", "dupuser")
                .param("password", "Test1234!"));

        mockMvc.perform(post("/api/users/signup")
                        .param("username", "dupuser")
                        .param("password", "Test1234!"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("비밀번호가 틀리면 401과 안내 문구를 준다")
    void 로그인_실패() throws Exception {
        mockMvc.perform(post("/api/users/signup")
                .param("username", "loginer")
                .param("password", "Test1234!"));

        mockMvc.perform(post("/api/users/login")
                        .contentType("application/json")
                        .content("{\"username\":\"loginer\",\"password\":\"WrongPw1!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("아이디 또는 비밀번호를 잘못 입력하셨습니다."));
    }

    @Test
    @DisplayName("로그인에 성공하면 JWT 쿠키를 내려준다")
    void 로그인_성공하면_쿠키를_받는다() throws Exception {
        mockMvc.perform(post("/api/users/signup")
                .param("username", "cookieman")
                .param("password", "Test1234!"));

        mockMvc.perform(post("/api/users/login")
                        .contentType("application/json")
                        .content("{\"username\":\"cookieman\",\"password\":\"Test1234!\"}"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .cookie().exists("JWT_TOKEN"));
    }
}

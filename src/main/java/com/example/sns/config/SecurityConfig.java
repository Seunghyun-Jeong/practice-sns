package com.example.sns.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;
    private final JwtAuthEntryPoint jwtAuthEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // 누구나 접근: 회원가입/로그인/로그아웃, 정적 리소스
                        .requestMatchers("/login", "/signup", "/css/**", "/js/**", "/uploads/**").permitAll()
                        .requestMatchers("/api/users/signup", "/api/users/login", "/api/users/logout").permitAll()
                        // 비로그인도 배지를 0으로 받아야 하므로 열어둔다 (컨트롤러에서 0 반환)
                        .requestMatchers("/api/notifications/unread-count").permitAll()
                        // 관리자 전용
                        .requestMatchers("/api/users/suspend/**", "/api/users/unsuspend/**").hasAuthority("ADMIN")
                        // 로그인 필요: 게시글/댓글/좋아요 쓰기, 내 좋아요 여부 조회
                        .requestMatchers(HttpMethod.POST, "/api/posts", "/api/posts/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/posts/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/posts/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/posts/*/like/me", "/api/posts/*/comments/*/like/me").authenticated()
                        // 로그인 필요: 팔로우 토글, 알림, 내 계정 관리
                        .requestMatchers(HttpMethod.POST, "/api/users/*/follow").authenticated()
                        .requestMatchers("/api/notifications/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/users/me").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/users/username").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/users/profile-image").authenticated()
                        // 그 외(피드/프로필/검색 등 읽기, SSR 페이지)는 공개 — 페이지 리다이렉트는 ViewController가 처리
                        .anyRequest().permitAll()
                )
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(jwtAuthEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

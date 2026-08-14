package com.example.sns.config;

import com.example.sns.entity.User;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class MyUserDetails implements org.springframework.security.core.userdetails.UserDetails {
    private final User user;

    public MyUserDetails(User user) {
        this.user = user;
    }

    /** 컨트롤러에서 토큰을 다시 파싱하지 않고 바로 쓰기 위한 접근자 */
    public Long getUserId() {
        return user.getId();
    }

    public String getRole() {
        return user.getRole().name();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(user.getRole().name()));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return !user.isSuspended(); }
}


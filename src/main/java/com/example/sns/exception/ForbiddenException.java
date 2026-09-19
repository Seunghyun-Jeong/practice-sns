package com.example.sns.exception;

/**
 * 로그인은 했지만 그 대상에 대해서는 할 수 없는 일일 때 던진다.
 *
 * 로그인 자체가 안 된 요청은 Spring Security 필터가 401로 먼저 끊기 때문에 여기까지 오지 않는다.
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}

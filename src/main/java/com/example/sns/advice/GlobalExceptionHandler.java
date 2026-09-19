package com.example.sns.advice;

import com.example.sns.exception.ForbiddenException;
import com.example.sns.exception.NotFoundException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * REST 컨트롤러가 던진 예외를 응답 코드와 안내 문구로 바꾼다.
 *
 * annotations = RestController.class 를 남겨둔 이유는, 화면을 그리는 ViewController까지
 * 여기로 끌어오면 페이지 요청에 JSON이 나가기 때문이다. 화면 쪽은 지금처럼 목록이나
 * 로그인으로 돌려보내는 방식을 그대로 쓴다.
 *
 * 본문은 전부 {"message": ...} 한 가지 모양으로 맞춘다. 예전에는 어떤 곳은 JSON을,
 * 어떤 곳은 문자열만 내보내서 화면이 문구를 읽지 못하고 기본 문구를 띄우는 곳이 있었다.
 */
@RestControllerAdvice(annotations = RestController.class)
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, String>> handleForbidden(ForbiddenException e) {
        return body(HttpStatus.FORBIDDEN, e.getMessage());
    }

    /** 요청 DTO의 @Size, @Pattern 같은 검증에 걸린 경우 */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<Map<String, String>> handleValidationException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(m -> m != null && !m.isBlank())
                .findFirst()
                .orElse("입력값이 올바르지 않습니다.");

        return body(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 나머지 잘못된 요청. 값이 규칙에 안 맞는 경우(빈 메시지, 잘못된 정지 기간)와
     * 지금 상태에서는 할 수 없는 일(이미 읽은 메시지 수정, 이미 처리된 신고)이 여기로 온다.
     *
     * 이 자리에 IllegalArgumentException을 두면 코드 버그로 난 같은 예외까지 400이 되어
     * 서버 오류가 잘못된 요청으로 보일 수 있다. 그래서 자원 없음과 권한 없음을 위처럼
     * 따로 빼서, 이 아래로는 "요청이 잘못됐다"는 뜻만 남도록 했다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    private ResponseEntity<Map<String, String>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(Map.of("message", message == null || message.isBlank() ? "요청을 처리할 수 없습니다." : message));
    }
}

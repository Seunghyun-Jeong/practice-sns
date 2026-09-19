package com.example.sns.exception;

/**
 * 요청이 가리키는 대상이 없을 때 던진다.
 *
 * 없는 것과 권한이 없는 것을 같은 예외로 던지면 컨트롤러가 응답 코드를 정할 근거가 없어서,
 * 부르는 쪽마다 400으로도 404로도 내보내게 된다. 실제로 같은 "찾을 수 없습니다"가
 * 채팅에서는 400, 게시글 삭제에서는 404로 나가고 있었다.
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}

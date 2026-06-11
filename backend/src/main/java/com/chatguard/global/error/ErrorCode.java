package com.chatguard.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증에 실패했습니다. 유효한 토큰이 필요합니다."),
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 채팅방입니다."),
    INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),

    MESSAGE_BLOCKED(HttpStatus.FORBIDDEN, "금칙어가 포함되어 있습니다."), 
    ROOM_MISMATCH(HttpStatus.BAD_REQUEST, "세션에 바인딩된 방 정보와 일치하지 않습니다."),
    INVALID_PAYLOAD(HttpStatus.BAD_REQUEST, "페이로드 형식이 잘못되었거나 제한을 초과했습니다.");

    private final HttpStatus status;
    private final String message;
}

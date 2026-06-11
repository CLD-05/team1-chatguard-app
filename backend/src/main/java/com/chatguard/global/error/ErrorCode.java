package com.chatguard.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증에 실패했습니다. 유효한 토큰이 필요합니다."),

    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 채팅방입니다.");

    private final HttpStatus status;
    private final String message;
}

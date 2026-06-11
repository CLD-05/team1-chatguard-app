package com.chatguard.global.error;

public record ErrorResponse(ErrorBody error) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(new ErrorBody(code, message));
    }

    public record ErrorBody(String code, String message) {
    }
}

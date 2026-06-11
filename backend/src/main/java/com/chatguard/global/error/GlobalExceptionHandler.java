package com.chatguard.global.error;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private ResponseEntity<Map<String, Object>> createErrorResponse(HttpStatus status, String code, String message) {
        Map<String, String> errorDetails = Map.of(
                "code", code,
                "message", message
        );
        return ResponseEntity.status(status).body(Map.of("error", errorDetails));
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<Map<String, Object>> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        return createErrorResponse(errorCode.getStatus(), errorCode.name(), errorCode.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("잘못된 정적 리소스 요청 무시: {}", e.getResourcePath());
        return createErrorResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", "요청하신 경로를 찾을 수 없습니다.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", "서버 내부 오류가 발생했습니다.");
    }
}

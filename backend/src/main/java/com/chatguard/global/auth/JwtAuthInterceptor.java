package com.chatguard.global.auth;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.chatguard.global.error.ErrorCode;
import com.chatguard.global.error.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {

    private final JwtProvider jwtProvider;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. CORS 통신을 위한 OPTIONS 요청은 통과시킵니다.
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        // 2. Authorization 헤더 꺼내기.
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return unauthorized(response);
        }

        // 3. 토큰 파싱 및 검증
        String token = header.substring(7);
        Claims claims = jwtProvider.getClaimsIfValid(token); // 1회 파싱으로 끝!

        if (claims == null) {
            return unauthorized(response);
        }

        // 4. 통과! AuthContext에 담아줍니다.
        AuthContext.setUserId(Long.parseLong(claims.getSubject()));
        return true;
    }

    // D29: 모든 4xx/5xx는 공통 에러 봉투 {error:{code,message}}를 반환한다.
    // 인터셉터는 GlobalExceptionHandler를 거치지 않으므로 401 봉투를 직접 직렬화한다.
    private boolean unauthorized(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try {
            String body = objectMapper.writeValueAsString(
                ErrorResponse.of(ErrorCode.UNAUTHORIZED.name(), ErrorCode.UNAUTHORIZED.getMessage()));
            response.getWriter().write(body);
        } catch (Exception e) {
            log.error("Failed to write UNAUTHORIZED error envelope", e);
        }
        return false;
    }
}

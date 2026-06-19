package com.chatguard.global.auth;

import com.chatguard.global.error.ErrorCode;
import com.chatguard.global.error.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminRoleInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        Claims claims = (Claims) request.getAttribute("jwtClaims");
        if (claims == null || !"ADMIN".equals(claims.get("role", String.class))) {
            return forbidden(response);
        }

        return true;
    }

    private boolean forbidden(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        try {
            String body = objectMapper.writeValueAsString(
                    ErrorResponse.of(ErrorCode.FORBIDDEN.name(), ErrorCode.FORBIDDEN.getMessage()));
            response.getWriter().write(body);
        } catch (Exception e) {
            log.error("Failed to write FORBIDDEN error envelope", e);
        }
        return false;
    }
}

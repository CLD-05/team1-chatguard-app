package com.chatguard.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;

class JwtAuthInterceptorTest {

    private JwtProvider jwtProvider;
    private JwtAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtProvider = mock(JwtProvider.class);
        interceptor = new JwtAuthInterceptor(jwtProvider, new ObjectMapper());
    }

    @Test
    void 헤더가_없으면_401_UNAUTHORIZED_봉투를_반환한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/rooms");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString())
            .contains("\"code\":\"UNAUTHORIZED\"")
            .contains("\"error\"");
    }

    @Test
    void 토큰이_무효하면_401_UNAUTHORIZED_봉투를_반환한다() throws Exception {
        when(jwtProvider.getClaimsIfValid("bad")).thenReturn((Claims) null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/rooms");
        request.addHeader("Authorization", "Bearer bad");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getContentAsString()).contains("\"code\":\"UNAUTHORIZED\"");
    }
}

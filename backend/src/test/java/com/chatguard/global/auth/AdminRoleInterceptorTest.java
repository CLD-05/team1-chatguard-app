package com.chatguard.global.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminRoleInterceptorTest {

    private AdminRoleInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new AdminRoleInterceptor(new ObjectMapper());
    }

    @Test
    void ADMIN_role이면_통과한다() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.get("role", String.class)).thenReturn("ADMIN");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/stats");
        request.setAttribute("jwtClaims", claims);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isTrue();
    }

    @Test
    void USER_role이면_403_FORBIDDEN_봉투를_반환한다() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.get("role", String.class)).thenReturn("USER");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/stats");
        request.setAttribute("jwtClaims", claims);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString())
                .contains("\"code\":\"FORBIDDEN\"")
                .contains("\"error\"");
    }

    @Test
    void claims가_없으면_403_FORBIDDEN_봉투를_반환한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/stats");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getContentAsString()).contains("\"code\":\"FORBIDDEN\"");
    }

    @Test
    void OPTIONS_요청은_무조건_통과한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/admin/stats");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isTrue();
    }
}

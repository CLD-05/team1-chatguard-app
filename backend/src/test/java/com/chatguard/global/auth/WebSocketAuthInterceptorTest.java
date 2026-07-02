package com.chatguard.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.chatguard.domain.room.repository.RoomRepository;

import io.jsonwebtoken.Claims;

class WebSocketAuthInterceptorTest {

    private static final String VALID_TOKEN = "valid-token";
    private static final long EXISTING_ROOM_ID = 1L;
    private static final long MISSING_ROOM_ID = 999L;

    private JwtProvider jwtProvider;
    private RoomRepository roomRepository;
    private WebSocketAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtProvider = mock(JwtProvider.class);
        roomRepository = mock(RoomRepository.class);
        interceptor = new WebSocketAuthInterceptor(jwtProvider, roomRepository);

        Claims claims = mock(Claims.class);
        lenient().when(claims.getSubject()).thenReturn("7");
        lenient().when(claims.get("display_name", String.class)).thenReturn("viewer7");
        lenient().when(jwtProvider.getClaimsIfValid(VALID_TOKEN)).thenReturn(claims);
        lenient().when(roomRepository.existsById(EXISTING_ROOM_ID)).thenReturn(true);
        lenient().when(roomRepository.existsById(MISSING_ROOM_ID)).thenReturn(false);
    }

    @Test
    void 유효한_토큰과_존재하는_방이면_핸드셰이크를_허용하고_세션에_바인딩한다() {
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = invoke(VALID_TOKEN, String.valueOf(EXISTING_ROOM_ID), servletResponse, attributes);

        assertThat(accepted).isTrue();
        assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(attributes).containsEntry("userId", 7L)
            .containsEntry("roomId", EXISTING_ROOM_ID)
            .containsEntry("displayName", "viewer7");
    }

    @Test
    void 토큰이_없으면_401로_업그레이드를_거부한다() {
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        boolean accepted = invoke(null, String.valueOf(EXISTING_ROOM_ID), servletResponse, new HashMap<>());

        assertThat(accepted).isFalse();
        assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void 토큰이_무효하면_401로_업그레이드를_거부한다() {
        when(jwtProvider.getClaimsIfValid("bad-token")).thenReturn(null);
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        boolean accepted = invoke("bad-token", String.valueOf(EXISTING_ROOM_ID), servletResponse, new HashMap<>());

        assertThat(accepted).isFalse();
        assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void room_id가_없으면_404로_업그레이드를_거부한다() {
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        boolean accepted = invoke(VALID_TOKEN, null, servletResponse, new HashMap<>());

        assertThat(accepted).isFalse();
        assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void room_id가_비숫자면_500이_아니라_404로_거부한다() {
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        boolean accepted = invoke(VALID_TOKEN, "abc", servletResponse, new HashMap<>());

        assertThat(accepted).isFalse();
        assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void 존재하지_않는_방이면_404로_업그레이드를_거부한다() {
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        boolean accepted = invoke(VALID_TOKEN, String.valueOf(MISSING_ROOM_ID), servletResponse, new HashMap<>());

        assertThat(accepted).isFalse();
        assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    private boolean invoke(String token, String roomId, MockHttpServletResponse servletResponse,
                           Map<String, Object> attributes) {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        if (token != null) {
            servletRequest.addHeader("Sec-WebSocket-Protocol", token);
        }
        if (roomId != null) {
            servletRequest.setParameter("room_id", roomId);
        }
        return interceptor.beforeHandshake(
            new ServletServerHttpRequest(servletRequest),
            new ServletServerHttpResponse(servletResponse),
            null,
            attributes
        );
    }
}

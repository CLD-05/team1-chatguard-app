package com.chatguard.domain.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RoomPresenceServiceTest {

    private StringRedisTemplate redisTemplate;
    private HashOperations<String, Object, Object> hashOps;
    private RoomPresenceService roomPresenceService;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        hashOps = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);

        roomPresenceService = new RoomPresenceService(redisTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(roomPresenceService, "roomChannelPrefix", "room:");
    }

    @Test
    void join_Redis_Hash에_userId와_displayName을_저장한다() {
        roomPresenceService.join(1L, 42L, "tester");

        verify(hashOps).put("room:1:members", "42", "tester");
    }

    @Test
    void join_후_presence_update_이벤트를_publish한다() {
        when(hashOps.entries("room:1:members")).thenReturn(Map.of("42", "tester"));

        roomPresenceService.join(1L, 42L, "tester");

        ArgumentCaptor<String> channelCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(channelCaptor.capture(), payloadCaptor.capture());

        assertThat(channelCaptor.getValue()).isEqualTo("room:1");
        assertThat(payloadCaptor.getValue())
            .contains("presence.update")
            .contains("room_id")
            .contains("count");
    }

    @Test
    void leave_Redis_Hash에서_userId를_제거한다() {
        roomPresenceService.leave(1L, 42L);

        verify(hashOps).delete("room:1:members", "42");
    }

    @Test
    void leave_후_presence_update_이벤트를_publish한다() {
        when(hashOps.entries("room:1:members")).thenReturn(Map.of());

        roomPresenceService.leave(1L, 42L);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq("room:1"), payloadCaptor.capture());

        assertThat(payloadCaptor.getValue()).contains("presence.update");
    }

    @Test
    void getSnapshot_count와_members를_올바르게_반환한다() {
        when(hashOps.entries("room:1:members")).thenReturn(Map.of(
            "10", "alice",
            "20", "bob"
        ));

        Map<String, Object> snapshot = roomPresenceService.getSnapshot(1L);

        assertThat(snapshot.get("room_id")).isEqualTo(1L);
        assertThat(snapshot.get("count")).isEqualTo(2);

        @SuppressWarnings("unchecked")
        var members = (java.util.List<Map<String, Object>>) snapshot.get("members");
        assertThat(members).hasSize(2);
        assertThat(members).anyMatch(m -> m.get("display_name").equals("alice"));
        assertThat(members).anyMatch(m -> m.get("display_name").equals("bob"));
    }

    @Test
    void getSnapshot_빈_방은_count_0을_반환한다() {
        when(hashOps.entries("room:1:members")).thenReturn(Map.of());

        Map<String, Object> snapshot = roomPresenceService.getSnapshot(1L);

        assertThat(snapshot.get("count")).isEqualTo(0);
    }
}

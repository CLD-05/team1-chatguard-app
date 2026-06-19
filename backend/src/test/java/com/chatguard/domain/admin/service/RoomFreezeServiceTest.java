package com.chatguard.domain.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RoomFreezeServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private RoomFreezeService roomFreezeService;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        roomFreezeService = new RoomFreezeService(redisTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(roomFreezeService, "roomChannelPrefix", "room:");
    }

    @Test
    void isFrozen_Redis에_true가_저장되어_있으면_true를_반환한다() {
        when(valueOps.get("room:1:frozen")).thenReturn("true");

        assertThat(roomFreezeService.isFrozen(1L)).isTrue();
    }

    @Test
    void isFrozen_Redis에_값이_없으면_false를_반환한다() {
        when(valueOps.get("room:1:frozen")).thenReturn(null);

        assertThat(roomFreezeService.isFrozen(1L)).isFalse();
    }

    @Test
    void setFrozen_true_호출시_Redis_key를_설정하고_room_freeze_이벤트를_publish한다() {
        roomFreezeService.setFrozen(1L, true);

        verify(valueOps).set("room:1:frozen", "true");

        ArgumentCaptor<String> channelCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(channelCaptor.capture(), payloadCaptor.capture());

        assertThat(channelCaptor.getValue()).isEqualTo("room:1");
        assertThat(payloadCaptor.getValue())
            .contains("\"type\"")
            .contains("room.freeze")
            .contains("\"frozen\"")
            .contains("true");
    }

    @Test
    void updateLocalCache_후_isFrozen은_Redis를_조회하지_않고_캐시값을_반환한다() {
        // Redis에는 아무 값도 없지만 로컬 캐시에만 true가 들어있으면 true 반환
        when(valueOps.get(any())).thenReturn(null);

        roomFreezeService.updateLocalCache(1L, true);

        assertThat(roomFreezeService.isFrozen(1L)).isTrue();
        // Redis 조회 없이 캐시에서 바로 반환했으므로 opsForValue().get() 호출 없어야 함
        verify(valueOps, never()).get(any());
    }

    @Test
    void setFrozen_false_호출시_Redis_key를_삭제하고_room_freeze_이벤트를_publish한다() {
        roomFreezeService.setFrozen(1L, false);

        verify(redisTemplate).delete("room:1:frozen");

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq("room:1"), payloadCaptor.capture());

        assertThat(payloadCaptor.getValue())
            .contains("room.freeze")
            .contains("false");
    }
}

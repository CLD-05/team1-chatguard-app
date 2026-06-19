package com.chatguard.domain.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.chatguard.domain.chat.dto.ChatSendDto;
import com.chatguard.domain.chat.repository.MessageRepository;
import com.chatguard.domain.chat.service.ChatService;
import com.chatguard.domain.chat.service.ChatService.SendMessageResult;
import com.chatguard.domain.moderation.entity.ModerationLog;
import com.chatguard.domain.moderation.entity.Verdict;
import com.chatguard.domain.moderation.repository.ModerationLogRepository;
import com.chatguard.domain.moderation.service.ModerationLogService;
import com.chatguard.domain.room.entity.Room;
import com.chatguard.domain.room.repository.RoomRepository;
import com.chatguard.domain.user.entity.User;
import com.chatguard.domain.user.repository.UserRepository;

import io.micrometer.core.instrument.MeterRegistry;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ModerationIntegrationTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ModerationLogRepository moderationLogRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private ModerationLogService moderationLogService;

    @Autowired
    private com.chatguard.domain.moderation.repository.BannedWordRepository bannedWordRepository;

    @Autowired
    private com.chatguard.domain.moderation.service.TextModerationService textModerationService;

    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private RedisMessageListenerContainer redisMessageListenerContainer;

    private Long roomId;
    private Long userId;

    @BeforeEach
    void setUp() {
        // Mock Redis behavior
        ListOperations<String, String> listOps = mock(ListOperations.class);
        when(stringRedisTemplate.opsForList()).thenReturn(listOps);
        when(listOps.leftPush(anyString(), anyString())).thenReturn(1L);

        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(any())).thenReturn(null); // 기본값: freeze 없음

        // 테스트 격리: 기존 데이터가 있으면 테스트 결과에 영향을 주므로 삭제
        messageRepository.deleteAll();
        moderationLogRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();
        bannedWordRepository.deleteAll();

        bannedWordRepository.save(com.chatguard.domain.moderation.entity.BannedWord.builder().word("badword").build());
        bannedWordRepository.save(com.chatguard.domain.moderation.entity.BannedWord.builder().word("욕설").build());
        textModerationService.refreshCache();

        Room room = roomRepository.save(Room.builder().name("Test Room").streamerName("Streamer").build());
        User user = userRepository.save(User.builder()
                .username("testuser")
                .password("password")
                .displayName("Tester")
                .build());
        roomId = room.getId();
        userId = user.getId();
    }

    @Test
    void 금칙어_포함_메시지는_차단되고_로그와_메트릭이_기록된다() {
        // Given: "badword"는 기본 금칙어로 설정되어 있음
        ChatSendDto dto = new ChatSendDto(roomId, "이 메시지에는 badword가 포함됨");

        // When
        SendMessageResult result = chatService.sendMessage(userId, "Tester", dto);

        // Then: 1) 결과가 BLOCKED_KEYWORD여야 함
        assertThat(result).isEqualTo(SendMessageResult.BLOCKED_KEYWORD);

        // Then: 2) messages 테이블에 저장되지 않아야 함
        assertThat(messageRepository.findAll()).isEmpty();

        // Then: 3) moderation_logs에 원문과 함께 기록되어야 함 (D25)
        List<ModerationLog> logs = moderationLogRepository.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getVerdict()).isEqualTo(Verdict.BLOCK);
        assertThat(logs.get(0).getContent()).contains("badword");

        // Then: 4) 메트릭 카운터가 증가해야 함 (B-2)
        double count = meterRegistry.get("chat_messages_total")
                .tag("result", "blocked_keyword")
                .counter().count();
        assertThat(count).isGreaterThan(0);
    }

    @Test
    void 한글_금칙어_포함_메시지도_정상적으로_차단된다() {
        // Given: "욕설"은 application-test.yml에 등록된 한글 금칙어
        ChatSendDto dto = new ChatSendDto(roomId, "이 메시지에는 욕설이 포함됨");

        // When
        SendMessageResult result = chatService.sendMessage(userId, "Tester", dto);

        // Then: 1) 결과가 BLOCKED_KEYWORD여야 함
        assertThat(result).isEqualTo(SendMessageResult.BLOCKED_KEYWORD);

        // Then: 2) messages 테이블에 저장되지 않아야 함
        assertThat(messageRepository.findAll()).isEmpty();
    }

    @Test
    void 정상_메시지는_통과되고_메트릭이_기록된다() {
        // Given
        ChatSendDto dto = new ChatSendDto(roomId, "안녕하세요 정상 메시지입니다.");

        // When
        SendMessageResult result = chatService.sendMessage(userId, "Tester", dto);

        // Then: 1) 결과가 SENT여야 함
        assertThat(result).isEqualTo(SendMessageResult.SENT);

        // Then: 2) messages 테이블에 저장되어야 함
        assertThat(messageRepository.findAll()).hasSize(1);

        // Then: 3) 메트릭(passed 카운터 및 지연시간 타이머)이 기록되어야 함
        double passedCount = meterRegistry.get("chat_messages_total")
                .tag("result", "passed")
                .counter().count();
        assertThat(passedCount).isGreaterThan(0);

        assertThat(meterRegistry.find("chat_broadcast_latency_seconds").timer()).isNotNull();
    }
}

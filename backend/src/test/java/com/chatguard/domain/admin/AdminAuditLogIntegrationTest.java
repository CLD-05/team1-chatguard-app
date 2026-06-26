package com.chatguard.domain.admin;

import com.chatguard.domain.admin.entity.AdminAuditLog;
import com.chatguard.domain.admin.repository.AdminAuditLogRepository;
import com.chatguard.domain.user.entity.User;
import com.chatguard.domain.user.entity.UserRole;
import com.chatguard.domain.user.repository.UserRepository;
import com.chatguard.global.auth.JwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAuditLogIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminAuditLogRepository adminAuditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private RedisMessageListenerContainer redisMessageListenerContainer;

    @MockBean
    private com.chatguard.domain.moderation.service.AdminKeywordService adminKeywordService;

    private String adminToken;
    private User adminUser;

    @BeforeEach
    void setUp() {
        // Mock Redis
        org.springframework.data.redis.core.ValueOperations<String, String> valueOps = mock(org.springframework.data.redis.core.ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(any())).thenReturn(null);

        org.springframework.data.redis.core.HashOperations<String, Object, Object> hashOps = mock(org.springframework.data.redis.core.HashOperations.class);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOps);
        when(hashOps.entries(any())).thenReturn(Map.of());

        // Clean tables
        adminAuditLogRepository.deleteAll();
        userRepository.deleteAll();

        // Create Admin User
        adminUser = userRepository.save(User.builder()
                .username("super_admin")
                .password("password")
                .displayName("SuperAdmin")
                .role(UserRole.ADMIN)
                .build());

        // Generate Valid JWT Token for the Admin User
        adminToken = jwtProvider.generateToken(adminUser.getId(), adminUser.getDisplayName(), "ADMIN");
    }

    @Test
    @DisplayName("금칙어 수동 추가 API 호출 시 비침습적 AOP를 통해 어드민 ID와 SpEL로 가공된 resourceId가 비동기로 DB에 적재된다.")
    void addKeywordAuditLogTest() throws Exception {
        // Given
        Map<String, String> requestMap = Map.of("word", "slang_word");
        String requestBody = objectMapper.writeValueAsString(requestMap);

        com.chatguard.domain.moderation.entity.BannedWord mockBannedWord = mock(com.chatguard.domain.moderation.entity.BannedWord.class);
        when(mockBannedWord.getId()).thenReturn(1L);
        when(mockBannedWord.getWord()).thenReturn("slang_word");
        when(mockBannedWord.getCreatedAt()).thenReturn(LocalDateTime.now());
        when(adminKeywordService.addBannedWord(any(), any())).thenReturn(mockBannedWord);

        // When
        mockMvc.perform(post("/api/admin/keywords")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        // Then: DB 비동기 저장 확인을 위해 Awaitility 사용
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            List<AdminAuditLog> logs = adminAuditLogRepository.findAll();
            assertThat(logs).isNotEmpty();

            AdminAuditLog log = logs.get(0);
            assertThat(log.getAdminId()).isEqualTo("super_admin"); // AuthContext + UserRepository 폴백 연동 검증
            assertThat(log.getAction()).isEqualTo("CREATE_KWD"); // @AdminLog("CREATE_KWD") 검증
            assertThat(log.getResourceId()).isEqualTo("slang_word"); // SpEL로 파싱된 word 검증
            assertThat(log.getCreatedAt()).isBeforeOrEqualTo(LocalDateTime.now(ZoneOffset.UTC)); // UTC 시간 규약 검증
        });
    }

    @Test
    @DisplayName("금칙어 삭제 API 호출 시 비침습적 AOP를 통해 delete 액션 및 id 파라미터가 resourceId로 수집된다.")
    void deleteKeywordAuditLogTest() throws Exception {
        // Given: 가상의 금칙어 ID
        Long targetWordId = 999L;

        // When
        mockMvc.perform(delete("/api/admin/keywords/" + targetWordId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Then: DB 비동기 저장 확인
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            List<AdminAuditLog> logs = adminAuditLogRepository.findAll();
            assertThat(logs).isNotEmpty();

            AdminAuditLog log = logs.get(0);
            assertThat(log.getAdminId()).isEqualTo("super_admin");
            assertThat(log.getAction()).isEqualTo("DELETE_KWD"); // @AdminLog("DELETE_KWD") 검증
            assertThat(log.getResourceId()).isEqualTo(targetWordId.toString()); // SpEL로 파싱된 id 검증
        });
    }
}

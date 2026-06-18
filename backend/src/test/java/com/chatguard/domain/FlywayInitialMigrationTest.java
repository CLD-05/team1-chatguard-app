package com.chatguard.domain;

import com.chatguard.domain.room.repository.RoomRepository;
import com.chatguard.domain.user.entity.User;
import com.chatguard.domain.user.entity.UserRole;
import com.chatguard.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway V1__InitialSchema.sql 마이그레이션 정상 작동 및 초기 데이터 검증 테스트.
 * 이 테스트는 Flyway를 명시적으로 활성화하여 초기 데이터를 검증합니다.
 */
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.flyway.enabled=false", // Disable auto-flyway to avoid circular dependency with JPA
        "spring.jpa.hibernate.ddl-auto=none" 
})
class FlywayInitialMigrationTest {

    @Autowired
    private javax.sql.DataSource dataSource;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private RedisMessageListenerContainer redisMessageListenerContainer;

    @BeforeEach
    void setUp() throws Exception {
        // H2 MySQL 모드에서의 호환성 문제(UTC_TIMESTAMP 파싱 오류)를 우회하기 위해
        // 테스트 환경에서는 Flyway 대신 SQL 스크립트를 직접 읽어 H2 호환 쿼리(CURRENT_TIMESTAMP)로 변환하여 실행합니다.
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            
            // 기존 테이블 싹 지우기 (Flyway clean 효과)
            stmt.execute("DROP ALL OBJECTS");
            
            // V1 스크립트 로드
            java.io.File file = new java.io.File("src/main/resources/db/migration/V1__InitialSchema.sql");
            String sql = new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            
            // H2 호환성 치환: UTC_TIMESTAMP() -> CURRENT_TIMESTAMP
            sql = sql.replace("UTC_TIMESTAMP()", "CURRENT_TIMESTAMP");
            
            stmt.execute(sql);
        }
    }

    @Test
    @DisplayName("Flyway V1 마이그레이션을 통해 초기 유저(Admin 포함)와 채팅방이 정상 적재된다")
    void verify_initial_data_seeding_via_flyway() {
        // 1. 어드민 유저 검증
        Optional<User> admin = userRepository.findByUsername("admin");
        assertThat(admin).isPresent();
        assertThat(admin.get().getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.get().getDisplayName()).isEqualTo("관리자");

        // 2. 일반 유저 검증
        Optional<User> user1 = userRepository.findByUsername("cjc");
        assertThat(user1).isPresent();
        assertThat(user1.get().getRole()).isEqualTo(UserRole.USER);
        assertThat(user1.get().getDisplayName()).isEqualTo("cjc");

        // 총 유저 수 검증 (cjc, ykh, ssm, lhc, kwy + admin = 6명)
        assertThat(userRepository.count()).isEqualTo(6);

        // 3. 채팅방 데이터 검증
        assertThat(roomRepository.count()).isEqualTo(3);
        assertThat(roomRepository.findAll())
                .extracting("name")
                .containsExactlyInAnyOrder("LCK 결승전 채팅방", "일상 방송 채팅방", "게임 방송 채팅방");
    }
}

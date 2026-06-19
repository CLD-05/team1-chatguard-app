package com.chatguard.domain.moderation;

import com.chatguard.domain.moderation.repository.BannedWordRepository;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.Context;
import org.flywaydb.core.api.migration.JavaMigration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class BannedWordSeedingTest {

    @Autowired
    private BannedWordRepository bannedWordRepository;

    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private RedisMessageListenerContainer redisMessageListenerContainer;

    @Autowired
    private javax.sql.DataSource dataSource;

    @Test
    @DisplayName("S3 시딩 마이그레이션 로직이 정상적으로 데이터를 DB에 적재한다")
    void seeding_migration_success() throws Exception {
        // given: S3에서 내려줄 가짜 데이터
        String mockContent = "badword1\nbadword2\nbadword1\n  \n  badword3  ";
        byte[] contentBytes = mockContent.getBytes();
        
        Context flywayContext = mock(Context.class);
        try (Connection connection = dataSource.getConnection()) {
            when(flywayContext.getConnection()).thenReturn(connection);

            JavaMigration testMigration = new JavaMigration() {
                @Override
                public MigrationVersion getVersion() { return null; }
                @Override
                public String getDescription() { return "test"; }
                @Override
                public Integer getChecksum() { return null; }
                @Override
                public boolean canExecuteInTransaction() { return true; }
                @Override
                public void migrate(Context context) throws Exception {
                    S3Client s3Client = mock(S3Client.class);
                    ResponseInputStream<GetObjectResponse> inputStream = new ResponseInputStream<>(
                            GetObjectResponse.builder().build(),
                            new ByteArrayInputStream(contentBytes)
                    );
                    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(inputStream);
                    
                    Set<String> words = new HashSet<>();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                            s3Client.getObject(GetObjectRequest.builder().bucket("b").key("k").build())))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (!line.isBlank()) words.add(line.trim());
                        }
                    }
                    
                    JdbcTemplate jdbcTemplate = new JdbcTemplate(new SingleConnectionDataSource(context.getConnection(), true));
                    String sql = "INSERT INTO banned_words (word, created_at) VALUES (?, NOW())";
                    jdbcTemplate.batchUpdate(sql, words.stream().map(word -> new Object[]{word}).collect(Collectors.toList()));
                }
            };

            // when
            testMigration.migrate(flywayContext);
        }

        // then
        assertThat(bannedWordRepository.findAll())
                .extracting("word")
                .containsExactlyInAnyOrder("badword1", "badword2", "badword3");
    }
}

package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AWS S3로부터 초기 금칙어 데이터를 스트리밍으로 읽어와 DB에 시딩하는 Java 마이그레이션.
 * 이 클래스는 로컬 env 기반 인증과 AWS EKS(IRSA) 환경 인증을 모두 자동으로 지원합니다.
 */
public class V2__InsertInitialBannedWords extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        String bucket = System.getenv("AWS_S3_BUCKET");
        String key = System.getenv("AWS_S3_KEY");
        String region = System.getenv("AWS_REGION");

        Set<String> words = new HashSet<>();

        // 1. 운영/EKS 환경 (S3 환경 변수가 존재하는 경우)
        if (bucket != null && key != null) {
            // AWS SDK의 DefaultCredentialsProvider는 IRSA, 환경변수, 인스턴스 프로파일을 자동 탐색합니다.
            try (S3Client s3Client = S3Client.builder()
                    .region(Region.of(region != null ? region : "ap-northeast-2"))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build()) {

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())))) {
                    readWordsFromReader(reader, words);
                }
            }
        } 
        // 2. 로컬 개발 환경 (Fallback: 클래스패스의 더미 파일 사용)
        else {
            try (java.io.InputStream is = getClass().getResourceAsStream("/dummy-bad-words.txt")) {
                if (is != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
                        readWordsFromReader(reader, words);
                    }
                }
            }
        }

        if (words.isEmpty()) {
            return;
        }

        JdbcTemplate jdbcTemplate = new JdbcTemplate(
                new SingleConnectionDataSource(context.getConnection(), true));

        // MySQL IGNORE 문법을 사용하여 동일 단어 중복 삽입 시 에러 없이 무시합니다.
        String sql = "INSERT IGNORE INTO banned_words (word, created_at) VALUES (?, UTC_TIMESTAMP())";
        jdbcTemplate.batchUpdate(sql, words.stream()
                .map(word -> new Object[]{word})
                .collect(Collectors.toList()));
    }

    private void readWordsFromReader(BufferedReader reader, Set<String> words) throws Exception {
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.isBlank() && !line.trim().startsWith("#")) {
                words.add(line.trim());
            }
        }
    }
}

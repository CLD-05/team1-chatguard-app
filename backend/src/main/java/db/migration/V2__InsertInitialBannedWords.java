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
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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

        // 로컬 환경에서 OS 환경 변수가 조회되지 않는 경우 .env 파일 수동 파싱 시도 (IDE 및 로컬 검증 편의성)
        if (bucket == null || key == null) {
            File envFile = new File(".env");
            if (envFile.exists()) {
                try (BufferedReader envReader = new BufferedReader(new FileReader(envFile))) {
                    String envLine;
                    while ((envLine = envReader.readLine()) != null) {
                        envLine = envLine.trim();
                        if (envLine.isEmpty() || envLine.startsWith("#")) {
                            continue;
                        }
                        int eqIdx = envLine.indexOf('=');
                        if (eqIdx > 0) {
                            String name = envLine.substring(0, eqIdx).trim();
                            String value = envLine.substring(eqIdx + 1).trim();
                            // 따옴표 제거
                            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                                value = value.substring(1, value.length() - 1);
                            } else if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
                                value = value.substring(1, value.length() - 1);
                            }
                            if ("AWS_S3_BUCKET".equals(name)) {
                                bucket = value;
                            } else if ("AWS_S3_KEY".equals(name)) {
                                key = value;
                            } else if ("AWS_REGION".equals(name)) {
                                region = value;
                            } else if ("AWS_PROFILE".equals(name)) {
                                System.setProperty("aws.profile", value);
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Failed to read local .env file manually: " + e.getMessage());
                }
            }
        }

        Set<String> words = new HashSet<>();

        // 1. 운영/EKS 환경 및 로컬 환경 변수가 구성된 경우 S3 시도
        if (bucket != null && key != null) {
            try {
                // AWS SDK의 DefaultCredentialsProvider는 IRSA, 환경변수, 인스턴스 프로파일을 자동 탐색합니다.
                try (S3Client s3Client = S3Client.builder()
                        .region(Region.of(region != null ? region : "ap-northeast-2"))
                        .credentialsProvider(DefaultCredentialsProvider.create())
                        .build()) {

                    // StandardCharsets.UTF_8 명시를 통해 한글 깨짐 방지
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                            s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build()),
                            StandardCharsets.UTF_8))) {
                        readWordsFromReader(reader, words);
                        System.out.println("Successfully seeded words from S3. Total: " + words.size());
                    }
                }
            } catch (Exception e) {
                // S3 에러 시 애플리케이션 크래시를 방지하고 로컬 리소스로 안전히 Fallback
                System.err.println("Failed to load banned words from S3 (" + bucket + "/" + key + "), falling back to local file. Error: " + e.getMessage());
                words.clear();
                loadFromLocalFile(words);
            }
        } 
        // 2. 로컬 개발 환경 (Fallback: 클래스패스의 더미 파일 사용)
        else {
            loadFromLocalFile(words);
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

    private void loadFromLocalFile(Set<String> words) throws Exception {
        try (java.io.InputStream is = getClass().getResourceAsStream("/dummy-bad-words.txt")) {
            if (is != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    readWordsFromReader(reader, words);
                    System.out.println("Seeded words from local dummy file. Total: " + words.size());
                }
            }
        }
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

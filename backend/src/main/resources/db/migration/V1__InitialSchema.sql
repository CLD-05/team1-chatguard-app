-- V1__InitialSchema.sql
-- 이미 존재하는 테이블들은 JPA가 생성하므로, 금칙어 테이블과 연관된 베이스만 정의함 (실제 운영시에는 전체 DDL 포함 권장)

CREATE TABLE IF NOT EXISTS banned_words (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    word VARCHAR(100) NOT NULL UNIQUE,
    created_by BIGINT,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_banned_words_created_by FOREIGN KEY (created_by) REFERENCES users(id)
);

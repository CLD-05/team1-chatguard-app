-- V1__InitialSchema.sql
-- 전체 시스템 스키마 정의 및 초기 데이터 주입

CREATE TABLE IF NOT EXISTS rooms (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    streamer_name VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL
);

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(60) NOT NULL,
    display_name VARCHAR(50),
    role ENUM('USER', 'ADMIN') NOT NULL DEFAULT 'USER',
    created_at DATETIME(6) NOT NULL
);

CREATE TABLE IF NOT EXISTS messages (
    id CHAR(26) PRIMARY KEY,
    content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    user_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_messages_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_messages_room FOREIGN KEY (room_id) REFERENCES rooms(id)
);

CREATE INDEX idx_room_created_at ON messages (room_id, created_at);

CREATE TABLE IF NOT EXISTS moderation_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id CHAR(26) NOT NULL,
    stage VARCHAR(20) NOT NULL,
    verdict VARCHAR(20) NOT NULL,
    score FLOAT,
    reason VARCHAR(200),
    model_version VARCHAR(50),
    content TEXT,
    checked_at DATETIME(6) NOT NULL
);

CREATE INDEX idx_moderation_logs_message_id ON moderation_logs (message_id);

CREATE TABLE IF NOT EXISTS banned_words (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    word VARCHAR(100) NOT NULL UNIQUE,
    created_by BIGINT,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_banned_words_created_by FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS admin_audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_id VARCHAR(255) NOT NULL,
    action VARCHAR(100) NOT NULL,
    resource_id VARCHAR(255),
    description TEXT,
    created_at DATETIME NOT NULL
);

CREATE INDEX idx_admin_audit_logs_admin_id ON admin_audit_logs (admin_id);
CREATE INDEX idx_admin_audit_logs_created_at ON admin_audit_logs (created_at);

-- 초기 데이터 주입 (기존 data.sql 통합)
INSERT INTO rooms (name, streamer_name, created_at)
VALUES 
    ('LCK 결승전 채팅방', '페이커', UTC_TIMESTAMP()),
    ('일상 방송 채팅방', '스트리머A', UTC_TIMESTAMP()),
    ('게임 방송 채팅방', '스트리머B', UTC_TIMESTAMP());

INSERT INTO users (username, password, display_name, role, created_at)
VALUES 
	('admin', '$2a$10$3b78qsO6Pt4GWd/iMZVe.eAyYpvEHYvRVYqAhPkTvQ.n1UDEyUgtm', '관리자', 'ADMIN', UTC_TIMESTAMP()),
    ('cjc', '$2a$10$MFsmUQgVQXst4u2vRR5bBOLESjrB3hEL9Qoz47byssMP5E9GzvYuO', 'cjc', 'USER', UTC_TIMESTAMP()),
    ('ykh', '$2a$10$B3avs4EroW3.gvXJttws9OorsnVBjDdwxp8S5q/XnHxpuzWvYkxpG', 'ykh', 'USER', UTC_TIMESTAMP()),
    ('ssm', '$2a$10$QfVQ8tSPnwSYzBW7455fL.4iHdwchB4u6KFMXBbeRH8QP6pL6H5k.', 'ssm', 'USER', UTC_TIMESTAMP()),
    ('lhc', '$2a$10$wbgFU/NZWgrxZr9WPhgGJutriDzG3SJzDq1SBJvlkD.Wo8yDkzMNS', 'lhc', 'USER', UTC_TIMESTAMP()),
    ('kwy', '$2a$10$oLHNou.QcqQjgk3DZIJA5.1CQ3eMgwBJzcTXRPKgBaRSF4uhP4p86', 'kwy', 'USER', UTC_TIMESTAMP());

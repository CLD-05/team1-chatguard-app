-- V3__DemoAccounts.sql
-- 시연(로컬 데모 녹화)용 테스트 계정 — 여러 명이 동시에 채팅하는 것처럼 보이게 하기 위한 용도.
-- password는 전부 lhc 계정과 동일한 값 — 같은 bcrypt 해시를 재사용해도 검증엔 문제없음.
-- 시연 끝나면 이 파일 삭제하고 DB 재생성(docker-compose down -v && up -d)할 것.

INSERT INTO users (username, password, display_name, role, created_at)
VALUES
    ('viewer1',  '$2a$10$wbgFU/NZWgrxZr9WPhgGJutriDzG3SJzDq1SBJvlkD.Wo8yDkzMNS', '김치찌개조아', 'USER', UTC_TIMESTAMP()),
    ('viewer2',  '$2a$10$wbgFU/NZWgrxZr9WPhgGJutriDzG3SJzDq1SBJvlkD.Wo8yDkzMNS', '레몬맛사탕', 'USER', UTC_TIMESTAMP()),
    ('viewer3',  '$2a$10$wbgFU/NZWgrxZr9WPhgGJutriDzG3SJzDq1SBJvlkD.Wo8yDkzMNS', '고양이집사22', 'USER', UTC_TIMESTAMP()),
    ('viewer4',  '$2a$10$wbgFU/NZWgrxZr9WPhgGJutriDzG3SJzDq1SBJvlkD.Wo8yDkzMNS', '얼음공주', 'USER', UTC_TIMESTAMP()),
    ('viewer5',  '$2a$10$wbgFU/NZWgrxZr9WPhgGJutriDzG3SJzDq1SBJvlkD.Wo8yDkzMNS', '냥냥펀치', 'USER', UTC_TIMESTAMP()),
    ('viewer6',  '$2a$10$wbgFU/NZWgrxZr9WPhgGJutriDzG3SJzDq1SBJvlkD.Wo8yDkzMNS', '치즈볼러버', 'USER', UTC_TIMESTAMP()),
    ('viewer7',  '$2a$10$wbgFU/NZWgrxZr9WPhgGJutriDzG3SJzDq1SBJvlkD.Wo8yDkzMNS', '새우깡매니아', 'USER', UTC_TIMESTAMP()),
    ('viewer8',  '$2a$10$wbgFU/NZWgrxZr9WPhgGJutriDzG3SJzDq1SBJvlkD.Wo8yDkzMNS', '무야호', 'USER', UTC_TIMESTAMP()),
    ('viewer9',  '$2a$10$wbgFU/NZWgrxZr9WPhgGJutriDzG3SJzDq1SBJvlkD.Wo8yDkzMNS', '전투민족93', 'USER', UTC_TIMESTAMP()),
    ('viewer10', '$2a$10$wbgFU/NZWgrxZr9WPhgGJutriDzG3SJzDq1SBJvlkD.Wo8yDkzMNS', '옆집아저씨', 'USER', UTC_TIMESTAMP()),
    ('viewer11', '$2a$10$wbgFU/NZWgrxZr9WPhgGJutriDzG3SJzDq1SBJvlkD.Wo8yDkzMNS', '감자튀김러버', 'USER', UTC_TIMESTAMP()),
    ('viewer12', '$2a$10$wbgFU/NZWgrxZr9WPhgGJutriDzG3SJzDq1SBJvlkD.Wo8yDkzMNS', '떡볶이가조아', 'USER', UTC_TIMESTAMP()),
    ('viewer13', '$2a$10$wbgFU/NZWgrxZr9WPhgGJutriDzG3SJzDq1SBJvlkD.Wo8yDkzMNS', '야채호빵맨', 'USER', UTC_TIMESTAMP()),
    ('viewer14', '$2a$10$wbgFU/NZWgrxZr9WPhgGJutriDzG3SJzDq1SBJvlkD.Wo8yDkzMNS', '겨울왕국매니아', 'USER', UTC_TIMESTAMP()),
    ('viewer15', '$2a$10$wbgFU/NZWgrxZr9WPhgGJutriDzG3SJzDq1SBJvlkD.Wo8yDkzMNS', '쿠키런유저', 'USER', UTC_TIMESTAMP()),
    ('viewer16', '$2a$10$wbgFU/NZWgrxZr9WPhgGJutriDzG3SJzDq1SBJvlkD.Wo8yDkzMNS', '미미2000', 'USER', UTC_TIMESTAMP()),
    ('viewer17', '$2a$10$wbgFU/NZWgrxZr9WPhgGJutriDzG3SJzDq1SBJvlkD.Wo8yDkzMNS', '김철수1234', 'USER', UTC_TIMESTAMP()),
    ('viewer18', '$2a$10$wbgFU/NZWgrxZr9WPhgGJutriDzG3SJzDq1SBJvlkD.Wo8yDkzMNS', '오늘의운세', 'USER', UTC_TIMESTAMP()),
    ('viewer19', '$2a$10$wbgFU/NZWgrxZr9WPhgGJutriDzG3SJzDq1SBJvlkD.Wo8yDkzMNS', '핑크퐁팬클럽', 'USER', UTC_TIMESTAMP()),
    ('viewer20', '$2a$10$wbgFU/NZWgrxZr9WPhgGJutriDzG3SJzDq1SBJvlkD.Wo8yDkzMNS', '달려라하니', 'USER', UTC_TIMESTAMP());

-- 시연용 방 이름/채널명 갱신 (기존 V1 시드는 그대로 두고 UPDATE만) + 방 3개 추가
UPDATE rooms SET name = '이스포츠 네이션스컵 결승 중계', streamer_name = 'MSI 중계채널' WHERE id = 1;
UPDATE rooms SET name = '축구대표팀 감독 인터뷰 LIVE', streamer_name = 'SportTV 뉴스' WHERE id = 2;
UPDATE rooms SET name = '발로란트 랭크 방송', streamer_name = '발로장인' WHERE id = 3;

INSERT INTO rooms (name, streamer_name, created_at)
VALUES
    ('엽떡+허니콤보+주먹밥 먹방!', '햄찌먹방', UTC_TIMESTAMP()),
    ('C1 솔랭방송', '김프로', UTC_TIMESTAMP()),
    ('저녁 라이브 노래방송', '기타치는수현', UTC_TIMESTAMP());

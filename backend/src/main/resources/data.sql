SET NAMES utf8mb4;

INSERT INTO rooms (name, streamer_name, created_at)
SELECT s.name, s.streamer_name, UTC_TIMESTAMP()
FROM (
  SELECT 'LCK 결승전 채팅방' AS name, '페이커'    AS streamer_name UNION ALL
  SELECT '일상 방송 채팅방',          '스트리머A'              UNION ALL
  SELECT '게임 방송 채팅방',          '스트리머B'
) AS s
WHERE NOT EXISTS (SELECT 1 FROM rooms r WHERE r.name = s.name);

INSERT INTO users (username, password, display_name, created_at)
SELECT u.username, u.password, u.display_name, UTC_TIMESTAMP()
FROM (
  SELECT 'user1' AS username, '$2a$10$MFsmUQgVQXst4u2vRR5bBOLESjrB3hEL9Qoz47byssMP5E9GzvYuO' AS password, '유저1' AS display_name UNION ALL
  SELECT 'user2', '$2a$10$B3avs4EroW3.gvXJttws9OorsnVBjDdwxp8S5q/XnHxpuzWvYkxpG', '유저2' UNION ALL
  SELECT 'user3', '$2a$10$QfVQ8tSPnwSYzBW7455fL.4iHdwchB4u6KFMXBbeRH8QP6pL6H5k.', '유저3' UNION ALL
  SELECT 'user4', '$2a$10$wbgFU/NZWgrxZr9WPhgGJutriDzG3SJzDq1SBJvlkD.Wo8yDkzMNS', '유저4' UNION ALL
  SELECT 'user5', '$2a$10$oLHNou.QcqQjgk3DZIJA5.1CQ3eMgwBJzcTXRPKgBaRSF4uhP4p86', '유저5' UNION ALL
  SELECT 'user6', '$2a$10$3b78qsO6Pt4GWd/iMZVe.eAyYpvEHYvRVYqAhPkTvQ.n1UDEyUgtm', '유저6'
) AS u
WHERE NOT EXISTS (
  SELECT 1 FROM users us WHERE us.username = u.username
);

SET NAMES utf8mb4;

INSERT INTO rooms (name, streamer_name, created_at)
SELECT name, streamer_name, NOW() FROM (
  SELECT 'LCK 결승전 채팅방' AS name, '페이커'    AS streamer_name UNION ALL
  SELECT '일상 방송 채팅방',          '스트리머A'              UNION ALL
  SELECT '게임 방송 채팅방',          '스트리머B'
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM rooms LIMIT 1);

package com.chatguard.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.chatguard.domain.chat.dto.MessageDto;
import com.chatguard.domain.chat.entity.Message;
import com.chatguard.domain.chat.entity.MessageStatus;
import com.chatguard.domain.moderation.service.ModerationLogService;
import com.chatguard.domain.moderation.service.TextModerationService;
import com.chatguard.domain.chat.queue.ModerationQueueProducer;
import com.chatguard.domain.chat.repository.MessageRepository;
import com.chatguard.domain.room.entity.Room;
import com.chatguard.domain.room.repository.RoomRepository;
import com.chatguard.domain.user.entity.User;
import com.chatguard.domain.user.repository.UserRepository;
import com.chatguard.global.error.CustomException;
import com.chatguard.global.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ChatServiceHistoryTest {

    // @EnableJpaAuditing은 메인 클래스(BackendApplication)에 있고 @DataJpaTest 슬라이스에서도
    // 활성화되므로(Room/User의 @CreatedDate 채워짐) 테스트에서 별도로 켜지 않는다.

    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private UserRepository userRepository;

    private ChatService chatService;
    private Long roomId;
    private Room room;
    private User user;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(
            messageRepository,
            mock(ModerationLogService.class),
            mock(TextModerationService.class),
            roomRepository,
            mock(ModerationQueueProducer.class),
            mock(StringRedisTemplate.class),
            new ObjectMapper(),
            mock(EntityManager.class)
        );
        room = roomRepository.save(Room.builder().name("room").streamerName("s").build());
        user = userRepository.save(User.builder().username("u").displayName("U").build());
        roomId = room.getId();
    }

    @Test
    void before가_없으면_최신_50건을_시간_오름차순으로_반환한다() {
        saveMessages(1, 60, MessageStatus.VISIBLE);

        List<MessageDto> history = chatService.getHistory(roomId, null, 50);

        assertThat(history).hasSize(50);
        assertThat(ids(history)).isEqualTo(idRange(11, 60)); // 최신 50건(11~60), 오름차순
    }

    @Test
    void before를_지정하면_그_직전_50건을_시간_오름차순으로_반환한다() {
        saveMessages(1, 60, MessageStatus.VISIBLE);

        List<MessageDto> history = chatService.getHistory(roomId, id(60), 50);

        assertThat(history).hasSize(50);
        // id < 60 인 1~59 중 최신 50건(10~59), 오름차순
        assertThat(ids(history)).isEqualTo(idRange(10, 59));
    }

    @Test
    void DELETED_메시지는_히스토리에서_제외한다() {
        saveMessage(1, MessageStatus.VISIBLE);
        saveMessage(2, MessageStatus.VISIBLE);
        saveMessage(3, MessageStatus.DELETED);
        saveMessage(4, MessageStatus.BLURRED);

        List<MessageDto> history = chatService.getHistory(roomId, null, 50);

        assertThat(ids(history)).containsExactly(id(1), id(2), id(4)); // 3(DELETED) 제외, BLURRED는 포함
    }

    @Test
    void limit이_50을_초과하면_50으로_제한한다() {
        saveMessages(1, 60, MessageStatus.VISIBLE);

        List<MessageDto> history = chatService.getHistory(roomId, null, 100);

        assertThat(history).hasSize(50);
        assertThat(ids(history)).isEqualTo(idRange(11, 60));
    }

    @Test
    void limit이_50보다_작으면_최신_그만큼만_오름차순으로_반환한다() {
        saveMessages(1, 60, MessageStatus.VISIBLE);

        List<MessageDto> history = chatService.getHistory(roomId, null, 3);

        assertThat(ids(history)).containsExactly(id(58), id(59), id(60));
    }

    @Test
    void 존재하지_않는_room_id면_ROOM_NOT_FOUND를_던진다() {
        long missingRoomId = roomId + 9999;

        assertThatThrownBy(() -> chatService.getHistory(missingRoomId, null, 50))
            .isInstanceOf(CustomException.class)
            .extracting(e -> ((CustomException) e).getErrorCode())
            .isEqualTo(ErrorCode.ROOM_NOT_FOUND);
    }

    // ── helpers ─────────────────────────────────────────────

    private void saveMessages(int from, int to, MessageStatus status) {
        for (int seq = from; seq <= to; seq++) {
            saveMessage(seq, status);
        }
    }

    private void saveMessage(int seq, MessageStatus status) {
        Message message = Message.builder()
            .id(id(seq))
            .room(room)
            .user(user)
            .content("m" + seq)
            .createdAt(LocalDateTime.now())
            .build();
        if (status != MessageStatus.VISIBLE) {
            message.changeStatus(status);
        }
        messageRepository.saveAndFlush(message);
    }

    /** 고정 폭 26자 id — 사전순 정렬이 곧 시퀀스 정렬이 되도록 0-패딩한다(ULID와 동일한 CHAR(26)). */
    private static String id(int seq) {
        return String.format("%026d", seq);
    }

    private static List<String> idRange(int fromInclusive, int toInclusive) {
        return java.util.stream.IntStream.rangeClosed(fromInclusive, toInclusive)
            .mapToObj(ChatServiceHistoryTest::id)
            .toList();
    }

    private static List<String> ids(List<MessageDto> history) {
        return history.stream().map(MessageDto::id).toList();
    }
}

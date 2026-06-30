package com.chatguard.domain.room.service;

import com.chatguard.domain.chat.service.RoomPresenceService;
import com.chatguard.domain.room.dto.RoomCreateRequest;
import com.chatguard.domain.room.dto.RoomResponse;
import com.chatguard.domain.room.entity.Room;
import com.chatguard.domain.room.repository.RoomRepository;
import com.chatguard.global.error.CustomException;
import com.chatguard.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomService {
    private final RoomRepository roomRepository;
    private final RoomPresenceService roomPresenceService;

    public List<RoomResponse> getRooms() {
        return roomRepository.findAll().stream()
            .map(room -> {
                int count = (int) roomPresenceService.getSnapshot(room.getId()).get("count");
                return new RoomResponse(
                    room.getId(),
                    room.getName(),
                    room.getStreamerName(),
                    room.getCreatedAt(),
                    count
                );
            })
            .toList();
    }

    public RoomResponse getRoom(Long roomId) {
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
        return RoomResponse.from(room);
    }

    @Transactional
    public RoomResponse createRoom(RoomCreateRequest request) {
        Room room = Room.builder()
            .name(required(request.name(), "name"))
            .streamerName(required(request.streamerName(), "streamerName"))
            .build();

        return RoomResponse.from(roomRepository.save(room));
    }

    private String required(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}

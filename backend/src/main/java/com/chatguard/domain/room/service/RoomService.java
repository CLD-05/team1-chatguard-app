package com.chatguard.domain.room.service;

import com.chatguard.domain.room.dto.RoomCreateRequest;
import com.chatguard.domain.room.dto.RoomResponse;
import com.chatguard.domain.room.entity.Room;
import com.chatguard.domain.room.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoomService {
    private final RoomRepository roomRepository;

    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> getRooms() {
        return roomRepository.findAll().stream()
            .map(RoomResponse::from)
            .toList();
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

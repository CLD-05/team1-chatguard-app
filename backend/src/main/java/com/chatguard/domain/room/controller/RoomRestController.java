package com.chatguard.domain.room.controller;

import com.chatguard.domain.room.dto.RoomCreateRequest;
import com.chatguard.domain.room.dto.RoomResponse;
import com.chatguard.domain.room.service.RoomService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomRestController {
    private final RoomService roomService;

    public RoomRestController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping
    public List<RoomResponse> getRooms() {
        return roomService.getRooms();
    }

    @PostMapping
    public RoomResponse createRoom(@RequestBody RoomCreateRequest request) {
        return roomService.createRoom(request);
    }

}

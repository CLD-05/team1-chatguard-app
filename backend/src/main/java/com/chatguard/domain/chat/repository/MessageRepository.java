package com.chatguard.domain.chat.repository;

import com.chatguard.domain.chat.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, String> {
    List<Message> findByRoomIdOrderByIdDesc(Long roomId, Pageable pageable);

    List<Message> findByRoomIdAndIdLessThanOrderByIdDesc(Long roomId, String before, Pageable pageable);

}

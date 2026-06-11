package com.chatguard.domain.chat.repository;

import com.chatguard.domain.chat.entity.Message;
import com.chatguard.domain.chat.entity.MessageStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, String> {

    List<Message> findByRoomIdAndIdLessThanAndStatusNotOrderByIdAsc(Long roomId, String beforeId, MessageStatus status, Pageable pageable);

    List<Message> findByRoomIdAndStatusNotOrderByIdAsc(Long roomId, MessageStatus status, Pageable pageable);
}

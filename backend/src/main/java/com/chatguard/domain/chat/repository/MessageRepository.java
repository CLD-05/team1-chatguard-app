package com.chatguard.domain.chat.repository;

import com.chatguard.domain.chat.entity.Message;
import com.chatguard.domain.chat.entity.MessageStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, String> {
    // 캐치업 윈도우 = 최신 N건을 id 내림차순으로 조회(D27). 서비스에서 시간 오름차순으로 뒤집어 반환한다.
    List<Message> findByRoomIdAndIdLessThanAndStatusNotOrderByIdDesc(Long roomId, String beforeId, MessageStatus status,
            Pageable pageable);

    List<Message> findByRoomIdAndStatusNotOrderByIdDesc(Long roomId, MessageStatus status, Pageable pageable);
}
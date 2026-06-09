package com.chatguard.domain.chat.entity;

import com.chatguard.domain.room.entity.Room;
import com.chatguard.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "messages", indexes = {
    @Index(name = "idx_room_created_at", columnList = "room_id, createdAt")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Message {

    @Id
    @Column(length = 26, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageStatus status = MessageStatus.VISIBLE;

    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public Message(String id, Room room, User user, String content, LocalDateTime createdAt) {
        this.id = id;
        this.room = room;
        this.user = user;
        this.content = content;
        this.createdAt = createdAt;
        this.status = MessageStatus.VISIBLE;
    }

    // AI 검열이나 키워드 필터링에 의해 사후에 상태가 변경될 때 사용할 비즈니스 메서드
    public void changeStatus(MessageStatus status) {
        this.status = status;
    }
}
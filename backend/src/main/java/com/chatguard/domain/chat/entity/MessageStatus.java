package com.chatguard.domain.chat.entity;

public enum MessageStatus {
    VISIBLE,  // 노출
    BLURRED,  // 블러 처리 (AI 2차 검열 차단건)
    DELETED   // 숨김 처리
}
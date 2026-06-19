package com.chatguard.domain.moderation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chatguard.domain.moderation.entity.BannedWord;
import com.chatguard.domain.moderation.repository.BannedWordRepository;

class TextModerationServiceTest {

    private BannedWordRepository bannedWordRepository;
    private TextModerationService textModerationService;

    @BeforeEach
    void setUp() {
        bannedWordRepository = mock(BannedWordRepository.class);
        textModerationService = new TextModerationService(bannedWordRepository);
    }

    @Test
    void DB의_금칙어를_소문자로_로컬캐시에_적재하고_검열판단시_정상동작한다() {
        // Given
        BannedWord word1 = BannedWord.builder().word("BadWord").build();
        BannedWord word2 = BannedWord.builder().word("욕설").build();
        when(bannedWordRepository.findAll()).thenReturn(List.of(word1, word2));

        // When
        textModerationService.refreshCache();

        // Then
        // 1) 대소문자 무관하게 캐시 매칭 작동 검증
        assertThat(textModerationService.judge("this contains badword")).isTrue();
        assertThat(textModerationService.judge("this contains BADWORD")).isTrue();
        
        // 2) 한글 금칙어 작동 검증
        assertThat(textModerationService.judge("욕설이 들어간 문장")).isTrue();

        // 3) 정상 문장은 통과 검증
        assertThat(textModerationService.judge("정상적인 메시지입니다.")).isFalse();
        
        // 4) 빈 문자열 및 null 가드 검증
        assertThat(textModerationService.judge("")).isFalse();
        assertThat(textModerationService.judge(null)).isFalse();
    }
}

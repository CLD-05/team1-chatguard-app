package com.chatguard.domain.moderation.service;

import com.chatguard.domain.moderation.entity.BannedWord;
import com.chatguard.domain.moderation.repository.BannedWordRepository;
import com.chatguard.domain.user.entity.User;
import com.chatguard.domain.user.entity.UserRole;
import com.chatguard.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminKeywordServiceTest {

    private BannedWordRepository bannedWordRepository;
    private UserRepository userRepository;
    private StringRedisTemplate redisTemplate;
    private TextModerationService textModerationService;
    private AdminKeywordService adminKeywordService;

    private User adminUser;

    @BeforeEach
    void setUp() {
        bannedWordRepository = mock(BannedWordRepository.class);
        userRepository = mock(UserRepository.class);
        redisTemplate = mock(StringRedisTemplate.class);
        textModerationService = mock(TextModerationService.class);

        adminKeywordService = new AdminKeywordService(
                bannedWordRepository,
                userRepository,
                redisTemplate,
                textModerationService
        );

        ReflectionTestUtils.setField(adminKeywordService, "configChannelPrefix", "config:");

        adminUser = User.builder()
                .username("admin")
                .password("password")
                .role(UserRole.ADMIN)
                .build();
    }

    @Test
    void 금칙어_전체를_조회한다() {
        // Given
        BannedWord word1 = BannedWord.builder().word("bad1").build();
        BannedWord word2 = BannedWord.builder().word("bad2").build();
        when(bannedWordRepository.findAll()).thenReturn(List.of(word1, word2));

        // When
        List<BannedWord> result = adminKeywordService.getBannedWords();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getWord()).isEqualTo("bad1");
    }

    @Test
    void 새로운_금칙어를_저장하고_이벤트를_발행한다() {
        // Given
        String newWord = "newBadWord";
        BannedWord expected = BannedWord.builder().word(newWord).createdBy(adminUser).build();

        when(bannedWordRepository.findByWord(newWord)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        when(bannedWordRepository.save(any(BannedWord.class))).thenReturn(expected);

        // When
        BannedWord result = adminKeywordService.addBannedWord(newWord, 1L);

        // Then
        assertThat(result.getWord()).isEqualTo(newWord);
        verify(bannedWordRepository, times(1)).save(any(BannedWord.class));
        verify(textModerationService, times(1)).refreshCache();
        verify(redisTemplate, times(1)).convertAndSend("config:banned-words", "invalidate");
    }

    @Test
    void 중복된_금칙어를_저장하면_예외가_발생한다() {
        // Given
        String duplicateWord = "duplicate";
        BannedWord existing = BannedWord.builder().word(duplicateWord).build();
        when(bannedWordRepository.findByWord(duplicateWord)).thenReturn(Optional.of(existing));

        // When & Then
        assertThatThrownBy(() -> adminKeywordService.addBannedWord(duplicateWord, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 존재하는 금칙어입니다.");

        verify(bannedWordRepository, never()).save(any(BannedWord.class));
        verify(textModerationService, never()).refreshCache();
        verify(redisTemplate, never()).convertAndSend(anyString(), anyString());
    }

    @Test
    void 금칙어를_삭제하고_이벤트를_발행한다() {
        // Given
        Long id = 1L;
        BannedWord word = BannedWord.builder().word("deleteMe").build();
        when(bannedWordRepository.findById(id)).thenReturn(Optional.of(word));

        // When
        adminKeywordService.deleteBannedWord(id);

        // Then
        verify(bannedWordRepository, times(1)).delete(word);
        verify(textModerationService, times(1)).refreshCache();
        verify(redisTemplate, times(1)).convertAndSend("config:banned-words", "invalidate");
    }

    @Test
    void 존재하지_않는_금칙어_삭제시_예외가_발생한다() {
        // Given
        Long id = 999L;
        when(bannedWordRepository.findById(id)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> adminKeywordService.deleteBannedWord(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("존재하지 않는 금칙어입니다.");

        verify(bannedWordRepository, never()).delete(any(BannedWord.class));
    }
}

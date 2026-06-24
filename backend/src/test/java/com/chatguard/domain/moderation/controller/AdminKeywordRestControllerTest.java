package com.chatguard.domain.moderation.controller;

import com.chatguard.domain.moderation.entity.BannedWord;
import com.chatguard.domain.moderation.service.AdminKeywordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AdminKeywordRestControllerTest {

    private AdminKeywordService adminKeywordService;
    private AdminKeywordRestController adminKeywordRestController;

    @BeforeEach
    void setUp() {
        adminKeywordService = mock(AdminKeywordService.class);
        adminKeywordRestController = new AdminKeywordRestController(adminKeywordService);
    }

    @Test
    void 금칙어_목록을_페이징_및_검색_조회하여_Response로_변환한다() {
        // Given
        BannedWord word1 = BannedWord.builder().word("bad1").build();
        BannedWord word2 = BannedWord.builder().word("bad2").build();
        List<BannedWord> content = List.of(word1, word2);
        Page<BannedWord> pageResult = new PageImpl<>(content, PageRequest.of(0, 10), content.size());

        when(adminKeywordService.getBannedWords(eq("bad"), any(Pageable.class))).thenReturn(pageResult);

        // When
        ResponseEntity<AdminKeywordRestController.BannedWordPageResponse> responseEntity =
                adminKeywordRestController.getKeywords(0, 10, "bad");

        // Then
        assertThat(responseEntity.getStatusCode().is2xxSuccessful()).isTrue();
        AdminKeywordRestController.BannedWordPageResponse body = responseEntity.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getContent()).hasSize(2);
        assertThat(body.getContent().get(0).getWord()).isEqualTo("bad1");
        assertThat(body.getContent().get(1).getWord()).isEqualTo("bad2");
        assertThat(body.getTotalPages()).isEqualTo(1);
        assertThat(body.getTotalElements()).isEqualTo(2);
        assertThat(body.getCurrentPage()).isEqualTo(0);
    }

    @Test
    void 새로운_금칙어를_추가하고_Response로_변환한다() {
        // Given
        String wordText = "newWord";
        Long userId = 1L;
        BannedWord savedWord = BannedWord.builder().word(wordText).build();
        when(adminKeywordService.addBannedWord(wordText, userId)).thenReturn(savedWord);

        AdminKeywordRestController.BannedWordRequest request =
                new AdminKeywordRestController.BannedWordRequest(wordText);

        // When
        ResponseEntity<AdminKeywordRestController.BannedWordResponse> responseEntity =
                adminKeywordRestController.addKeyword(userId, request);

        // Then
        assertThat(responseEntity.getStatusCode().is2xxSuccessful()).isTrue();
        AdminKeywordRestController.BannedWordResponse body = responseEntity.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getWord()).isEqualTo(wordText);
        verify(adminKeywordService, times(1)).addBannedWord(wordText, userId);
    }

    @Test
    void 금칙어를_삭제한다() {
        // Given
        Long wordId = 1L;

        // When
        ResponseEntity<Void> responseEntity = adminKeywordRestController.deleteKeyword(wordId);

        // Then
        assertThat(responseEntity.getStatusCode().is2xxSuccessful()).isTrue();
        verify(adminKeywordService, times(1)).deleteBannedWord(wordId);
    }
}

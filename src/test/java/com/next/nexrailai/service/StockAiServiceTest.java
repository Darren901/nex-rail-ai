package com.next.nexrailai.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class StockAiServiceTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private StockApiService stockApiService;

    @InjectMocks
    private StockAiService stockAiService;

    @Test
    void stockAiService_shouldBeCreated_withoutException() {
        // 基本實例化測試（ChatClient 是複雜的 fluent API，深層 mock 測試另類）
        assertThat(stockAiService).isNotNull();
    }
}

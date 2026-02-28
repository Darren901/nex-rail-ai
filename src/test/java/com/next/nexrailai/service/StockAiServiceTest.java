package com.next.nexrailai.service;

import com.next.nexrailai.component.StockTools;
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
    private StockTools stockTools;

    @InjectMocks
    private StockAiService stockAiService;

    @Test
    void stockAiService_shouldBeInstantiable_withToolsInjected() {
        // StockAiService 注入 StockTools（不再依賴 StockApiService 直接呼叫）
        assertThat(stockAiService).isNotNull();
    }
}

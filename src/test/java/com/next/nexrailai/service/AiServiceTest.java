package com.next.nexrailai.service;

import com.next.nexrailai.component.ThsrFunctionTools;
import com.next.nexrailai.config.PromptConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.tool.ToolCallback;

import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiServiceTest {

    @Mock
    private ChatClient chatClient;
    @Mock
    private PromptConfig promptConfig;
    @Mock
    private ThsrFunctionTools thsrFunctionTools;
    @Mock
    private RateLimitService rateLimitService;
    @Mock
    private ChatMemory chatMemory;

    @InjectMocks
    private AiService aiService;

    @Test
    void chat_ShouldReturnResponse_WhenSuccess() {
        // Arrange
        String userId = "U123";
        String message = "Hi";

        when(rateLimitService.getRemainingQuota(userId)).thenReturn(10);
        when(rateLimitService.getRemainingMonthlyQuota(userId)).thenReturn(5);
        
        when(promptConfig.getSystem()).thenReturn(Map.of("text", "System Prompt"));

        // Mock ChatClient Fluent API
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(Consumer.class))).thenReturn(requestSpec); // SystemSpec consumer
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.advisors(any(Consumer.class))).thenReturn(requestSpec);
        
        // Fix ambiguity for toolCallbacks (it takes varargs)
        when(requestSpec.toolCallbacks(any(ToolCallback[].class))).thenReturn(requestSpec);
        
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("AI Response");

        // Act
        String result = aiService.chat(userId, message);

        // Assert
        assertEquals("AI Response", result);
    }
}

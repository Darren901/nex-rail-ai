package com.next.nexrailai.integration;

import com.linecorp.bot.messaging.model.FlexMessage;
import com.linecorp.bot.messaging.model.Message;
import com.linecorp.bot.messaging.model.TextMessage;
import com.next.nexrailai.context.ThsrContextHolder;
import com.next.nexrailai.dto.FareResultDTO;
import com.next.nexrailai.dto.ThsrSummaryDTO;
import com.next.nexrailai.service.AiService;
import com.next.nexrailai.service.LineMessageService;
import com.next.nexrailai.service.LineService;
import com.next.nexrailai.service.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
public class ThsrSearchIntegrationTest {

    @Autowired
    private LineService lineService;

    @MockBean
    private AiService aiService;

    @MockBean
    private LineMessageService lineMessageService;

    @MockBean
    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        // Default Rate Limit Pass
        when(rateLimitService.tryConsume(anyString())).thenReturn(true);
    }

    @Test
    void testSearchFlow_Timetable() {
        // Arrange
        String userId = "U100";
        String message = "查明天台北到高雄";
        String replyToken = "token_search";

        // Mock AI Service to simulate tool execution setting context
        when(aiService.chat(eq(userId), eq(message))).thenAnswer(invocation -> {
            FareResultDTO fare = new FareResultDTO("全票", "成人", "標準", 1490);
            ThsrSummaryDTO summary = new ThsrSummaryDTO("101", "10:00", "11:30", "有位", "有位", List.of(fare));
            
            String futureDate = java.time.LocalDate.now().plusDays(30).toString();
            ThsrContextHolder.ThsrSearchResult result = ThsrContextHolder.ThsrSearchResult.builder()
                    .trains(List.of(summary))
                    .origin("台北")
                    .destination("高雄")
                    .trainDate(futureDate)
                    .build();
            ThsrContextHolder.set(result);
            return "這是您的查詢結果";
        });

        // Act
        lineService.handleUserMessage(userId, message, replyToken);

        // Assert
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(lineMessageService).reply(eq(replyToken), captor.capture());
        
        Message response = captor.getValue();
        assertTrue(response instanceof FlexMessage, "Should respond with Flex Message for timetable");
    }

    @Test
    void testRateLimitFlow() {
        // Arrange
        String userId = "U_BROKE";
        String message = "查票";
        String replyToken = "token_limit";

        when(rateLimitService.tryConsume(userId)).thenReturn(false);

        // Act
        lineService.handleUserMessage(userId, message, replyToken);

        // Assert
        verify(aiService, never()).chat(anyString(), anyString());
        
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(lineMessageService).reply(eq(replyToken), captor.capture());
        
        Message response = captor.getValue();
        assertTrue(response instanceof TextMessage);
        assertTrue(((TextMessage) response).text().contains("額度已用完"));
    }
}

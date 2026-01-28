package com.next.nexrailai.service;

import com.linecorp.bot.messaging.model.FlexMessage;
import com.linecorp.bot.messaging.model.Message;
import com.linecorp.bot.messaging.model.TextMessage;
import com.next.nexrailai.context.ThsrContextHolder;
import com.next.nexrailai.dto.FareResultDTO;
import com.next.nexrailai.dto.ThsrSummaryDTO;
import com.next.nexrailai.dto.ThsrTimetableDTO;
import com.next.nexrailai.jpa.service.UserMemoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LineServiceTest {

    @Mock
    private LineMessageService lineMessageService;
    @Mock
    private AiService aiService;
    @Mock
    private ScheduleService scheduleService;
    @Mock
    private UserMemoryService userMemoryService;
    @Mock
    private RateLimitService rateLimitService;

    @InjectMocks
    private LineService lineService;

    @AfterEach
    void tearDown() {
        ThsrContextHolder.clear();
    }

    @Test
    void handleUserMessage_ShouldReplyText_WhenNoContextData() {
        // Arrange
        String userId = "U123";
        String message = "你好";
        String replyToken = "token";

        when(rateLimitService.tryConsume(userId)).thenReturn(true);
        when(aiService.chat(userId, message)).thenReturn("你好！我是高鐵助理。");

        // Act
        lineService.handleUserMessage(userId, message, replyToken);

        // Assert
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(lineMessageService).reply(eq(replyToken), messageCaptor.capture());
        
        Message captured = messageCaptor.getValue();
        assertTrue(captured instanceof TextMessage);
        assertEquals("你好！我是高鐵助理。", ((TextMessage) captured).text());
    }

    @Test
    void handleUserMessage_ShouldReplyFlex_WhenBookingLinkExists() {
        // Arrange
        String userId = "U123";
        String message = "訂票";
        String replyToken = "token";

        when(rateLimitService.tryConsume(userId)).thenReturn(true);
        
        // Mock AI service to set Context
        when(aiService.chat(userId, message)).thenAnswer(inv -> {
            ThsrContextHolder.ThsrSearchResult result = ThsrContextHolder.ThsrSearchResult.builder()
                    .bookingLink("http://booking")
                    .origin("台北")
                    .destination("高雄")
                    .trainDate("2023-12-01")
                    .trainTime("10:00")
                    .trainNumber("101")
                    .build();
            ThsrContextHolder.set(result);
            return "已為您產生訂票連結";
        });

        // Act
        lineService.handleUserMessage(userId, message, replyToken);

        // Assert
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(lineMessageService).reply(eq(replyToken), messageCaptor.capture());
        
        Message captured = messageCaptor.getValue();
        assertTrue(captured instanceof FlexMessage); // Booking confirmation is Flex
    }

    @Test
    void handleUserMessage_ShouldReplyFlex_WhenTimetableExists() {
        // Arrange
        String userId = "U123";
        String message = "查班次";
        String replyToken = "token";

        when(rateLimitService.tryConsume(userId)).thenReturn(true);

        // Mock AI service to set Context
        when(aiService.chat(userId, message)).thenAnswer(inv -> {
            FareResultDTO fare = new FareResultDTO("全票", "成人", "標準", 1490);
            ThsrSummaryDTO summary = new ThsrSummaryDTO("101", "10:00", "11:30", "有位", "有位", List.of(fare));
            
            ThsrContextHolder.ThsrSearchResult result = ThsrContextHolder.ThsrSearchResult.builder()
                    .trains(List.of(summary))
                    .origin("台北")
                    .destination("高雄")
                    .trainDate("2023-12-01")
                    .build();
            
            ThsrContextHolder.set(result);
            return "這是查詢結果";
        });

        // Act
        lineService.handleUserMessage(userId, message, replyToken);

        // Assert
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(lineMessageService).reply(eq(replyToken), messageCaptor.capture());

        Message captured = messageCaptor.getValue();
        assertTrue(captured instanceof FlexMessage); // Timetable is Flex
    }
}

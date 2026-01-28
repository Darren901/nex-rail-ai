package com.next.nexrailai.scheduled.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.bot.messaging.model.Message;
import com.linecorp.bot.messaging.model.TextMessage;
import com.next.nexrailai.dto.FareResultDTO;
import com.next.nexrailai.dto.ThsrSummaryDTO;
import com.next.nexrailai.dto.ai.SearchRequest;
import com.next.nexrailai.jpa.entity.ScheduleTask;
import com.next.nexrailai.service.LineMessageService;
import com.next.nexrailai.service.SystemConfigService;
import com.next.nexrailai.service.ThsrTicketService;
import com.next.nexrailai.utils.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketMonitorExecutorTest {

    @Mock
    private ThsrTicketService ticketService;
    @Mock
    private LineMessageService lineMessageService;
    @Mock
    private SystemConfigService systemConfigService;

    @InjectMocks
    private TicketMonitorExecutor executor;

    @BeforeEach
    void setUp() {
        // Setup JsonUtil for static access
        new JsonUtil().setObjectMapper(new ObjectMapper());
    }

    @Test
    void execute_ShouldComplete_WhenTicketsFound() {
        // Arrange
        // Use future date to avoid expiration
        String date = java.time.LocalDate.now().plusDays(1).toString();
        String payload = "{\"from\":\"台北\",\"to\":\"高雄\",\"date\":\"" + date + "\",\"time\":\"10:00\"}";
        ScheduleTask task = ScheduleTask.builder()
                .id(1L)
                .userId("U123")
                .payload(payload)
                .status(ScheduleTask.TaskStatus.PENDING)
                .build();

        // Mock Ticket Service returning available seats
        ThsrSummaryDTO train = mock(ThsrSummaryDTO.class);
        when(train.standardSeatStatus()).thenReturn("有位"); // Not "客滿"
        when(train.departureTime()).thenReturn("10:00");
        when(train.trainNo()).thenReturn("123");
        
        when(ticketService.searchTickets(any(SearchRequest.class))).thenReturn(List.of(train));
        when(ticketService.generateDeepLink(any(), any(), any(), any(), any())).thenReturn("http://link");

        // Act
        executor.execute(task);

        // Assert
        verify(lineMessageService).pushMessage(eq("U123"), any(Message.class));
        assertEquals(ScheduleTask.TaskStatus.COMPLETED, task.getStatus());
    }

    @Test
    void execute_ShouldRetry_WhenNoTicketsFound() {
        // Arrange
        String date = java.time.LocalDate.now().plusDays(1).toString();
        String payload = "{\"from\":\"台北\",\"to\":\"高雄\",\"date\":\"" + date + "\",\"time\":\"10:00\"}";
        ScheduleTask task = ScheduleTask.builder()
                .id(1L)
                .userId("U123")
                .payload(payload)
                .status(ScheduleTask.TaskStatus.PENDING)
                .triggerTime(LocalDateTime.now())
                .build();

        // Mock Ticket Service returning NO available seats (Empty list or all Full)
        when(ticketService.searchTickets(any(SearchRequest.class))).thenReturn(Collections.emptyList());
        when(systemConfigService.getInt("ticket_monitor_interval_seconds")).thenReturn(60);

        // Act
        executor.execute(task);

        // Assert
        verify(lineMessageService, never()).pushMessage(any(), any(Message.class));
        // Task status should remain PENDING (or not changed to COMPLETED/EXPIRED)
        assertEquals(ScheduleTask.TaskStatus.PENDING, task.getStatus());
        // Trigger time should be updated (we can't easily assert the exact time, but we assume setTriggerTime was called)
    }

    @Test
    void execute_ShouldExpire_WhenPastDepartureTime() {
        // Arrange
        // Date is in the past
        String payload = "{\"from\":\"台北\",\"to\":\"高雄\",\"date\":\"2000-01-01\",\"time\":\"10:00\"}";
        ScheduleTask task = ScheduleTask.builder()
                .id(1L)
                .userId("U123")
                .payload(payload)
                .status(ScheduleTask.TaskStatus.PENDING)
                .build();

        // Act
        executor.execute(task);

        // Assert
        verify(lineMessageService).pushMessage(eq("U123"), any(TextMessage.class));
        assertEquals(ScheduleTask.TaskStatus.EXPIRED, task.getStatus());
        verify(ticketService, never()).searchTickets(any());
    }
}

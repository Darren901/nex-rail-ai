package com.next.nexrailai.integration;

import com.linecorp.bot.messaging.model.FlexMessage;
import com.linecorp.bot.messaging.model.Message;
import com.linecorp.bot.messaging.model.TextMessage;
import com.next.nexrailai.dto.ThsrSummaryDTO;
import com.next.nexrailai.dto.ai.SearchRequest;
import com.next.nexrailai.jpa.entity.ScheduleTask;
import com.next.nexrailai.jpa.repository.ScheduleTaskRepository;
import com.next.nexrailai.service.LineMessageService;
import com.next.nexrailai.service.ScheduleService;
import com.next.nexrailai.service.SystemConfigService;
import com.next.nexrailai.service.ThsrTicketService;
import com.next.nexrailai.utils.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpClientErrorException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
public class ScheduleTaskIntegrationTest {

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private ScheduleTaskRepository scheduleTaskRepository;

    @MockitoBean
    private ThsrTicketService thsrTicketService;

    @MockitoBean
    private LineMessageService lineMessageService;

    @MockitoBean
    private SystemConfigService systemConfigService;

    @BeforeEach
    void setUp() {
        scheduleTaskRepository.deleteAll();
        when(systemConfigService.getInt("ticket_monitor_interval_seconds")).thenReturn(60);
    }

    @Test
    void testTicketMonitor_TicketsFound() {
        // Arrange
        String userId = "U_MONITOR_SUCCESS";
        String futureDate = LocalDateTime.now().plusDays(1).toLocalDate().toString();
        SearchRequest request = new SearchRequest("台北", "高雄", futureDate, "10:00", null, null, null);
        String payload = JsonUtil.toJson(request);

        ScheduleTask task = ScheduleTask.builder()
                .userId(userId)
                .triggerTime(LocalDateTime.now().minusSeconds(1)) // Due immediately
                .content("Monitor Task")
                .taskType(ScheduleTask.TaskType.TICKET_MONITOR)
                .status(ScheduleTask.TaskStatus.PENDING)
                .payload(payload)
                .build();
        scheduleTaskRepository.save(task);

        ThsrSummaryDTO availableTrain = new ThsrSummaryDTO(
                "101", "10:00", "11:30", "有位", "有位", List.of()
        );
        when(thsrTicketService.searchTickets(any(SearchRequest.class)))
                .thenReturn(List.of(availableTrain));
        
        when(thsrTicketService.generateDeepLink(any(), any(), any(), any(), any()))
                .thenReturn("http://booking.link");

        // Act
        scheduleService.processScheduledTasks();

        // Assert
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(lineMessageService).pushMessage(eq(userId), captor.capture());
        
        Message message = captor.getValue();
        assertTrue(message instanceof FlexMessage, "Should send Flex Message when tickets found");

        ScheduleTask updatedTask = scheduleTaskRepository.findById(task.getId()).orElseThrow();
        assertEquals(ScheduleTask.TaskStatus.COMPLETED, updatedTask.getStatus());
    }

    @Test
    void testTicketMonitor_TicketsNotFound_Retry() {
        // Arrange
        String userId = "U_MONITOR_RETRY";
        String futureDate = LocalDateTime.now().plusDays(1).toLocalDate().toString();
        SearchRequest request = new SearchRequest("台北", "高雄", futureDate, "12:00", null, null, null);
        String payload = JsonUtil.toJson(request);

        ScheduleTask task = ScheduleTask.builder()
                .userId(userId)
                .triggerTime(LocalDateTime.now().minusSeconds(1))
                .taskType(ScheduleTask.TaskType.TICKET_MONITOR)
                .status(ScheduleTask.TaskStatus.PENDING)
                .payload(payload)
                .build();
        scheduleTaskRepository.save(task);

        // Mock return with "Full" seats (standard seat status contains "客滿")
        ThsrSummaryDTO fullTrain = new ThsrSummaryDTO(
                "202", "12:00", "13:30", "客滿", "有位", List.of()
        );
        when(thsrTicketService.searchTickets(any(SearchRequest.class)))
                .thenReturn(List.of(fullTrain));

        // Act
        scheduleService.processScheduledTasks();

        // Assert
        verify(lineMessageService, never()).pushMessage(eq(userId), any());
        
        ScheduleTask updatedTask = scheduleTaskRepository.findById(task.getId()).orElseThrow();
        assertEquals(ScheduleTask.TaskStatus.PENDING, updatedTask.getStatus(), "Status should remain PENDING");
        assertTrue(updatedTask.getTriggerTime().isAfter(LocalDateTime.now()), "Trigger time should be extended");
    }

    @Test
    void testTicketMonitor_Expired() {
        // Arrange
        String userId = "U_MONITOR_EXPIRED";
        // Date in the past
        SearchRequest request = new SearchRequest("台北", "高雄", "2020-01-01", "10:00", null, null, null);
        String payload = JsonUtil.toJson(request);

        ScheduleTask task = ScheduleTask.builder()
                .userId(userId)
                .triggerTime(LocalDateTime.now().minusSeconds(1))
                .taskType(ScheduleTask.TaskType.TICKET_MONITOR)
                .status(ScheduleTask.TaskStatus.PENDING)
                .payload(payload)
                .build();
        scheduleTaskRepository.save(task);

        // Act
        scheduleService.processScheduledTasks();

        // Assert
        verify(thsrTicketService, never()).searchTickets(any()); // Should expire before search
        
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(lineMessageService).pushMessage(eq(userId), captor.capture());
        
        Message message = captor.getValue();
        assertTrue(message instanceof TextMessage);
        assertTrue(((TextMessage) message).text().contains("監控結束通知"));

        ScheduleTask updatedTask = scheduleTaskRepository.findById(task.getId()).orElseThrow();
        assertEquals(ScheduleTask.TaskStatus.EXPIRED, updatedTask.getStatus());
    }

    @Test
    void testTicketMonitor_ApiError() {
        // Arrange
        String userId = "U_MONITOR_ERROR";
        String futureDate = LocalDateTime.now().plusDays(1).toLocalDate().toString();
        SearchRequest request = new SearchRequest("台北", "高雄", futureDate, "15:00", null, null, null);
        
        ScheduleTask task = ScheduleTask.builder()
                .userId(userId)
                .triggerTime(LocalDateTime.now().minusSeconds(1))
                .taskType(ScheduleTask.TaskType.TICKET_MONITOR)
                .status(ScheduleTask.TaskStatus.PENDING)
                .payload(JsonUtil.toJson(request))
                .build();
        scheduleTaskRepository.save(task);

        when(thsrTicketService.searchTickets(any()))
                .thenThrow(new HttpClientErrorException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "API Error"));

        // Act
        scheduleService.processScheduledTasks();

        // Assert
        ScheduleTask updatedTask = scheduleTaskRepository.findById(task.getId()).orElseThrow();
        // Should retry (PENDING) with delay
        assertEquals(ScheduleTask.TaskStatus.PENDING, updatedTask.getStatus());
        assertTrue(updatedTask.getTriggerTime().isAfter(LocalDateTime.now()), "Should retry later on error");
    }
}

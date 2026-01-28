package com.next.nexrailai.scheduled.strategy;

import com.next.nexrailai.common.ApBusinessException;
import com.next.nexrailai.jpa.entity.ScheduleTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleTaskExecutorFactoryTest {

    @Mock
    private ScheduleTaskExecutor reminderExecutor;
    @Mock
    private ScheduleTaskExecutor ticketMonitorExecutor;

    private ScheduleTaskExecutorFactory factory;

    @BeforeEach
    void setUp() {
        // Setup Mocks
        when(reminderExecutor.getSupportedTaskType()).thenReturn(ScheduleTask.TaskType.REMINDER);
        when(ticketMonitorExecutor.getSupportedTaskType()).thenReturn(ScheduleTask.TaskType.TICKET_MONITOR);

        // Inject List
        factory = new ScheduleTaskExecutorFactory(List.of(reminderExecutor, ticketMonitorExecutor));
        factory.init(); // Manually trigger init because we are not using Spring Context
    }

    @Test
    void getExecutor_ShouldReturnReminderExecutor_WhenTypeIsReminder() {
        ScheduleTaskExecutor result = factory.getExecutor(ScheduleTask.TaskType.REMINDER);
        assertEquals(reminderExecutor, result);
    }

    @Test
    void getExecutor_ShouldReturnMonitorExecutor_WhenTypeIsMonitor() {
        ScheduleTaskExecutor result = factory.getExecutor(ScheduleTask.TaskType.TICKET_MONITOR);
        assertEquals(ticketMonitorExecutor, result);
    }

    @Test
    void getExecutor_ShouldThrowException_WhenTypeIsNotFound() {
        // Create a factory with empty list
        ScheduleTaskExecutorFactory emptyFactory = new ScheduleTaskExecutorFactory(List.of());
        emptyFactory.init();

        assertThrows(RuntimeException.class, () -> {
            emptyFactory.getExecutor(ScheduleTask.TaskType.REMINDER);
        });
    }
}

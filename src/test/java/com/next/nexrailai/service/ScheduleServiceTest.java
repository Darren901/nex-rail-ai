package com.next.nexrailai.service;

import com.next.nexrailai.jpa.entity.ScheduleTask;
import com.next.nexrailai.jpa.repository.ScheduleTaskRepository;
import com.next.nexrailai.scheduled.strategy.ScheduleTaskExecutor;
import com.next.nexrailai.scheduled.strategy.ScheduleTaskExecutorFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock
    private ScheduleTaskRepository repository;
    @Mock
    private ScheduleTaskExecutorFactory factory;
    @Mock
    private RateLimitService rateLimitService;

    @InjectMocks
    private ScheduleService scheduleService;

    @Test
    void processScheduledTasks_ShouldDelegateToExecutor() {
        // Arrange
        ScheduleTask task1 = ScheduleTask.builder().id(1L).taskType(ScheduleTask.TaskType.REMINDER).build();
        ScheduleTask task2 = ScheduleTask.builder().id(2L).taskType(ScheduleTask.TaskType.TICKET_MONITOR).build();

        when(repository.findByStatusAndTriggerTimeBefore(eq(ScheduleTask.TaskStatus.PENDING), any()))
                .thenReturn(List.of(task1, task2));

        ScheduleTaskExecutor reminderExecutor = mock(ScheduleTaskExecutor.class);
        ScheduleTaskExecutor monitorExecutor = mock(ScheduleTaskExecutor.class);

        when(factory.getExecutor(ScheduleTask.TaskType.REMINDER)).thenReturn(reminderExecutor);
        when(factory.getExecutor(ScheduleTask.TaskType.TICKET_MONITOR)).thenReturn(monitorExecutor);

        // Act
        scheduleService.processScheduledTasks();

        // Assert
        verify(reminderExecutor).execute(task1);
        verify(monitorExecutor).execute(task2);
        verify(repository).saveAll(anyList());
    }
}

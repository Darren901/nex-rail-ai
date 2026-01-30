package com.next.nexrailai.service;

import com.next.nexrailai.jpa.entity.ScheduleTask;
import com.next.nexrailai.jpa.repository.ScheduleTaskRepository;
import com.next.nexrailai.scheduled.strategy.ScheduleTaskExecutor;
import com.next.nexrailai.scheduled.strategy.ScheduleTaskExecutorFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
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
        
        // 驗證每個任務都被單獨儲存，而不是批量儲存 (避免 Race Condition)
        verify(repository).save(task1);
        verify(repository).save(task2);
        verify(repository, never()).saveAll(anyList());
    }

    @Test
    void processScheduledTasks_ShouldRetryWithBackoff_FirstFailure() {
        // Arrange
        LocalDateTime originalTime = LocalDateTime.now();
        ScheduleTask task = ScheduleTask.builder()
                .id(3L)
                .taskType(ScheduleTask.TaskType.REMINDER)
                .retryCount(0)
                .triggerTime(originalTime)
                .build();

        when(repository.findByStatusAndTriggerTimeBefore(any(), any())).thenReturn(List.of(task));
        
        ScheduleTaskExecutor executor = mock(ScheduleTaskExecutor.class);
        when(factory.getExecutor(any())).thenReturn(executor);
        doThrow(new RuntimeException("Execution Failed")).when(executor).execute(any());

        // Act
        scheduleService.processScheduledTasks();

        // Assert
        ArgumentCaptor<ScheduleTask> taskCaptor = ArgumentCaptor.forClass(ScheduleTask.class);
        verify(repository).save(taskCaptor.capture());
        
        ScheduleTask savedTask = taskCaptor.getValue();
        assertEquals(1, savedTask.getRetryCount());
        // 驗證時間增加了約 5 分鐘 (容許誤差)
        long minutesDiff = ChronoUnit.MINUTES.between(originalTime, savedTask.getTriggerTime());
        assertTrue(minutesDiff >= 4 && minutesDiff <= 6, "First retry should be around 5 minutes");
    }

    @Test
    void processScheduledTasks_ShouldRetryWithBackoff_SecondFailure() {
        // Arrange
        LocalDateTime originalTime = LocalDateTime.now();
        ScheduleTask task = ScheduleTask.builder()
                .id(4L)
                .taskType(ScheduleTask.TaskType.REMINDER)
                .retryCount(1) // 已經重試過 1 次
                .triggerTime(originalTime)
                .build();

        when(repository.findByStatusAndTriggerTimeBefore(any(), any())).thenReturn(List.of(task));
        
        ScheduleTaskExecutor executor = mock(ScheduleTaskExecutor.class);
        when(factory.getExecutor(any())).thenReturn(executor);
        doThrow(new RuntimeException("Execution Failed Again")).when(executor).execute(any());

        // Act
        scheduleService.processScheduledTasks();

        // Assert
        ArgumentCaptor<ScheduleTask> taskCaptor = ArgumentCaptor.forClass(ScheduleTask.class);
        verify(repository).save(taskCaptor.capture());
        
        ScheduleTask savedTask = taskCaptor.getValue();
        assertEquals(2, savedTask.getRetryCount());
        // 驗證時間增加了約 15 分鐘 (5 * 3)
        long minutesDiff = ChronoUnit.MINUTES.between(originalTime, savedTask.getTriggerTime());
        assertTrue(minutesDiff >= 14 && minutesDiff <= 16, "Second retry should be around 15 minutes");
    }

    @Test
    void processScheduledTasks_ShouldFail_WhenMaxRetriesExceeded() {
        // Arrange
        ScheduleTask task = ScheduleTask.builder()
                .id(5L)
                .taskType(ScheduleTask.TaskType.REMINDER)
                .retryCount(2) // 已經重試 2 次，這次失敗就是第 3 次
                .status(ScheduleTask.TaskStatus.PENDING)
                .build();

        when(repository.findByStatusAndTriggerTimeBefore(any(), any())).thenReturn(List.of(task));
        
        ScheduleTaskExecutor executor = mock(ScheduleTaskExecutor.class);
        when(factory.getExecutor(any())).thenReturn(executor);
        doThrow(new RuntimeException("Final Failure")).when(executor).execute(any());

        // Act
        scheduleService.processScheduledTasks();

        // Assert
        ArgumentCaptor<ScheduleTask> taskCaptor = ArgumentCaptor.forClass(ScheduleTask.class);
        verify(repository).save(taskCaptor.capture());
        
        ScheduleTask savedTask = taskCaptor.getValue();
        assertEquals(3, savedTask.getRetryCount());
        assertEquals(ScheduleTask.TaskStatus.FAILED, savedTask.getStatus());
    }
}

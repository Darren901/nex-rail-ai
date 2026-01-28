package com.next.nexrailai.scheduled.strategy;

import com.linecorp.bot.messaging.model.TextMessage;
import com.next.nexrailai.jpa.entity.ScheduleTask;
import com.next.nexrailai.service.LineMessageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReminderExecutorTest {

    @Mock
    private LineMessageService lineMessageService;

    @InjectMocks
    private ReminderExecutor reminderExecutor;

    @Test
    void execute_ShouldPushMessageAndCompleteTask() {
        // Arrange
        ScheduleTask task = ScheduleTask.builder()
                .userId("U123")
                .content("Test Content")
                .status(ScheduleTask.TaskStatus.PENDING)
                .build();

        // Act
        reminderExecutor.execute(task);

        // Assert
        verify(lineMessageService).pushMessage(eq("U123"), any(TextMessage.class));
        assertEquals(ScheduleTask.TaskStatus.EXECUTED, task.getStatus());
    }
}

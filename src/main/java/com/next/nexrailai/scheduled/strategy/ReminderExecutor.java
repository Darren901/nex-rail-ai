package com.next.nexrailai.scheduled.strategy;

import com.linecorp.bot.messaging.model.TextMessage;
import com.next.nexrailai.jpa.entity.ScheduleTask;
import com.next.nexrailai.service.LineMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ReminderExecutor implements ScheduleTaskExecutor {

    private final LineMessageService lineMessageService;

    @Override
    public ScheduleTask.TaskType getSupportedTaskType() {
        return ScheduleTask.TaskType.REMINDER;
    }

    @Override
    public void execute(ScheduleTask task) {
        log.info(">>>> [ReminderExecutor] Executing reminder for user: {}", task.getUserId());
        String message = "⏰ 提醒事項：\n" + task.getContent();
        lineMessageService.pushMessage(task.getUserId(), new TextMessage(message));
        
        task.setStatus(ScheduleTask.TaskStatus.EXECUTED);
    }
}

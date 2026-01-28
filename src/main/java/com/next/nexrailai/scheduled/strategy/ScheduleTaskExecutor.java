package com.next.nexrailai.scheduled.strategy;

import com.next.nexrailai.jpa.entity.ScheduleTask;

public interface ScheduleTaskExecutor {
    
    /**
 * Identifies the ScheduleTask.TaskType this executor handles.
 *
 * @return the {@link ScheduleTask.TaskType} that this executor supports
 */
    ScheduleTask.TaskType getSupportedTaskType();

    /**
 * Execute the given scheduled task.
 *
 * @param task the scheduled task to execute
 */
    void execute(ScheduleTask task);
}
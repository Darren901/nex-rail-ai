package com.next.nexrailai.scheduled.strategy;

import com.next.nexrailai.jpa.entity.ScheduleTask;

public interface ScheduleTaskExecutor {
    
    /**
     * 取得此執行器支援的任務類型
     */
    ScheduleTask.TaskType getSupportedTaskType();

    /**
     * 執行任務
     */
    void execute(ScheduleTask task);
}

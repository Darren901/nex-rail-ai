package com.next.nexrailai.scheduled;

import com.next.nexrailai.aspect.DistributedLock;
import com.next.nexrailai.jpa.entity.ScheduleTask;
import com.next.nexrailai.jpa.repository.ScheduleTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Component
@Slf4j
@RequiredArgsConstructor
public class CacheCleanupScheduler {

    private final ScheduleTaskRepository scheduleTaskRepository;

    // 每天凌晨 4 點清理 30 天前的歷史任務
    // 使用分散式鎖確保多實例環境下只有一個實例執行
    @Scheduled(cron = "0 0 4 * * ?")
    @DistributedLock(key = "cleanup-old-tasks")
    @Transactional
    public void cleanupOldTasks() {
        try {
            LocalDateTime threshold = LocalDateTime.now().minusDays(30);
            List<ScheduleTask.TaskStatus> statusesToDelete = List.of(
                    ScheduleTask.TaskStatus.EXECUTED,
                    ScheduleTask.TaskStatus.COMPLETED,
                    ScheduleTask.TaskStatus.CANCELLED,
                    ScheduleTask.TaskStatus.FAILED,
                    ScheduleTask.TaskStatus.EXPIRED
            );
            
            scheduleTaskRepository.deleteByStatusInAndCreatedAtBefore(statusesToDelete, threshold);
            log.info(">>>> [Cleanup] 已清理 30 天前的歷史任務");
        } catch (Exception e) {
            log.error(">>>> [Cleanup] 清理歷史任務失敗", e);
        }
    }
}

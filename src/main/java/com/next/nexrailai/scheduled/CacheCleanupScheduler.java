package com.next.nexrailai.scheduled;

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

    private final StringRedisTemplate redisTemplate;
    private final ScheduleTaskRepository scheduleTaskRepository;

    // 每天凌晨 3 點清理昨天的時刻表 cache
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupOldTimetableCache() {
        try {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            String pattern = "tdx:timetable:*:" + yesterday + "*";
            Set<String> oldKeys = redisTemplate.keys(pattern);

            if (oldKeys != null && !oldKeys.isEmpty()) {
                redisTemplate.delete(oldKeys);
                log.info("已清理 {} 個昨天的時刻表 cache", oldKeys.size());
            }
        } catch (Exception e) {
            log.error("清理舊 cache 失敗", e);
        }
    }

    // 每天凌晨 4 點清理 30 天前的歷史任務
    @Scheduled(cron = "0 0 4 * * ?")
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

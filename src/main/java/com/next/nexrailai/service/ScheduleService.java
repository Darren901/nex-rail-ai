package com.next.nexrailai.service;

import com.next.nexrailai.aspect.DistributedLock;
import com.next.nexrailai.common.ApBusinessException;
import com.next.nexrailai.common.Constant;
import com.next.nexrailai.dto.ai.SearchRequest;
import com.next.nexrailai.jpa.entity.ScheduleTask;
import com.next.nexrailai.jpa.repository.ScheduleTaskRepository;
import com.next.nexrailai.scheduled.strategy.ScheduleTaskExecutor;
import com.next.nexrailai.scheduled.strategy.ScheduleTaskExecutorFactory;
import com.next.nexrailai.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleTaskRepository repository;
    private final RateLimitService rateLimitService;
    private final ScheduleTaskExecutorFactory taskExecutorFactory;

    // 限制最大並發數 怕 TDX 被打爆吐 429
    private final Semaphore semaphore = new Semaphore(20);

    /**
     * 新增一般提醒任務
     */
    @Transactional
    public ScheduleTask createReminder(String userId, LocalDateTime triggerTime, String content) {
        if (!rateLimitService.tryConsumeMonthlyNotification(userId)) {
            throw new ApBusinessException(Constant.RCODE.MONTHLY_QUOTA_EXCEEDED);
        }

        ScheduleTask task = ScheduleTask.builder()
                .userId(userId)
                .triggerTime(triggerTime)
                .content(content)
                .taskType(ScheduleTask.TaskType.REMINDER)
                .status(ScheduleTask.TaskStatus.PENDING)
                .build();
        return repository.save(task);
    }

    /**
     * 新增查票監控任務
     */
    @Transactional
    public ScheduleTask createTicketMonitor(String userId, LocalDateTime triggerTime, SearchRequest searchRequest) {
        if (!rateLimitService.tryConsumeMonthlyNotification(userId)) {
            throw new ApBusinessException(Constant.RCODE.MONTHLY_QUOTA_EXCEEDED);
        }

        String payload;
        try {
            payload = JsonUtil.toJson(searchRequest);
        } catch (Exception e) {
            throw new RuntimeException("無法序列化 SearchRequest", e);
        }

        ScheduleTask task = ScheduleTask.builder()
                .userId(userId)
                .triggerTime(triggerTime)
                .content("監控車票: " + searchRequest.from() + " ➔ " + searchRequest.to() + " (" + searchRequest.date() + ")")
                .taskType(ScheduleTask.TaskType.TICKET_MONITOR)
                .payload(payload)
                .status(ScheduleTask.TaskStatus.PENDING)
                .build();
        return repository.save(task);
    }

    /**
     * 查詢使用者的特定類型任務
     */
    public List<ScheduleTask> getTasksByUserAndType(String userId, ScheduleTask.TaskType type) {
        return repository.findByUserIdAndStatus(userId, ScheduleTask.TaskStatus.PENDING).stream()
                .filter(t -> t.getTaskType() == type)
                .collect(Collectors.toList());
    }

    /**
     * 取消特定任務
     */
    @Transactional
    public void cancelTask(Long id, String userId) {
        repository.findById(id).ifPresent(task -> {
            if (task.getUserId().equals(userId)) {
                task.setStatus(ScheduleTask.TaskStatus.CANCELLED);
                repository.save(task);
                log.info(">>>> [Schedule] 已取消任務 ID: {}", id);
            }
        });
    }

    @Transactional
    public void cancelAllPendingTasks(String userId) {
        List<ScheduleTask> tasks = repository.findByUserIdAndStatus(userId, ScheduleTask.TaskStatus.PENDING);
        tasks.forEach(t -> t.setStatus(ScheduleTask.TaskStatus.CANCELLED));
        repository.saveAll(tasks);
    }

    /**
     * 定時檢查並執行任務
     * 使用分散式鎖確保多實例環境下只有一個實例執行
     * 使用虛擬執行緒並行處理 避免阻塞
     */
    @Scheduled(fixedRate = 30000)
    @DistributedLock(key = "process-scheduled-tasks")
    public void processScheduledTasks() {
        LocalDateTime now = LocalDateTime.now();
        List<ScheduleTask> tasks = repository.findByStatusAndTriggerTimeBefore(ScheduleTask.TaskStatus.PENDING, now);

        if (tasks.isEmpty()) {
            return;
        }

        log.info(">>>> [Schedule] 發現 {} 個到期任務，準備並行執行...", tasks.size());

        // try-with-resources 會自動等待所有任務完成
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            tasks.forEach(task -> executor.submit(() -> executeTaskWithRateLimit(task)));
        } // 這裡會 Block 直到所有虛擬執行緒完成

        repository.saveAll(tasks);
        log.info(">>>> [Schedule] 批量執行完畢，更新 {} 筆任務狀態", tasks.size());
    }

    /**
     * 執行單一任務（帶速率限制和錯誤處理）
     *
     * 1. Semaphore 速率限制（最多 20 個並發）
     * 2. 中斷處理
     * 3. 失敗重試（5 分鐘後）
     */
    private void executeTaskWithRateLimit(ScheduleTask task) {
        try {
            // 取得許可證 (Rate Limiting)
            semaphore.acquire();
            try {
                ScheduleTaskExecutor taskExecutor = taskExecutorFactory.getExecutor(task.getTaskType());
                taskExecutor.execute(task);
            } finally {
                semaphore.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn(">>>> [Schedule] 任務執行被中斷 ID: {}", task.getId());
        } catch (Exception e) {
            log.error(">>>> [Schedule] 任務執行失敗 ID: {}", task.getId(), e);
            // 失敗重試：5 分鐘後
            task.setTriggerTime(LocalDateTime.now().plusMinutes(5));
        }
    }
}

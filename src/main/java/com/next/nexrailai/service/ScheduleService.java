package com.next.nexrailai.service;

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
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleTaskRepository repository;
    private final RateLimitService rateLimitService;
    private final ScheduleTaskExecutorFactory taskExecutorFactory;

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
     * Executes pending scheduled tasks whose trigger time has passed.
     *
     * For each task, obtains a ScheduleTaskExecutor from the factory and invokes it. If execution fails,
     * logs the error and reschedules the task for 5 minutes later. All processed tasks are persisted.
     */
    @Scheduled(fixedRate = 30000)
    public void processScheduledTasks() {
        LocalDateTime now = LocalDateTime.now();
        List<ScheduleTask> tasks = repository.findByStatusAndTriggerTimeBefore(ScheduleTask.TaskStatus.PENDING, now);

        if (!tasks.isEmpty()) {
            log.info(">>>> [Schedule] 發現 {} 個到期任務，準備執行...", tasks.size());
        }

        for (ScheduleTask task : tasks) {
            try {
                ScheduleTaskExecutor executor = taskExecutorFactory.getExecutor(task.getTaskType());
                executor.execute(task);
            } catch (Exception e) {
                log.error(">>>> [Schedule] 任務執行失敗 ID: {}", task.getId(), e);
                // 失敗重試：5 分鐘後
                task.setTriggerTime(LocalDateTime.now().plusMinutes(5));
            }
        }
        repository.saveAll(tasks);
    }
}
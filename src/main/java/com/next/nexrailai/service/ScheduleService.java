package com.next.nexrailai.service;

import com.linecorp.bot.messaging.client.MessagingApiClient;
import com.linecorp.bot.messaging.model.Message;
import com.linecorp.bot.messaging.model.PushMessageRequest;
import com.linecorp.bot.messaging.model.TextMessage;
import com.next.nexrailai.dto.ThsrSummaryDTO;
import com.next.nexrailai.dto.ai.SearchRequest;
import com.next.nexrailai.jpa.entity.ScheduleTask;
import com.next.nexrailai.jpa.repository.ScheduleTaskRepository;
import com.next.nexrailai.utils.FlexMessageUtil;
import com.next.nexrailai.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ScheduleService {

    @Value("${app.base-url}")
    private String baseUrl;

    private final ScheduleTaskRepository repository;
    private final MessagingApiClient messagingApiClient;
    private final ThsrTicketService ticketService;

    /**
     * 新增一般提醒任務
     */
    @Transactional
    public ScheduleTask createReminder(String userId, LocalDateTime triggerTime, String content) {
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
        String payload;
        try {
            payload = JsonUtil.toJson(searchRequest);
        } catch (Exception e) {
            throw new RuntimeException("無法序列化 SearchRequest", e);
        }

        ScheduleTask task = ScheduleTask.builder()
                .userId(userId)
                .triggerTime(triggerTime)
                .content("監控車票: " + searchRequest.from() + " -> " + searchRequest.to() + " (" + searchRequest.date() + ")")
                .taskType(ScheduleTask.TaskType.TICKET_MONITOR)
                .payload(payload)
                .status(ScheduleTask.TaskStatus.PENDING)
                .build();
        return repository.save(task);
    }

    /**
     * 查詢使用者的待辦提醒
     */
    public List<ScheduleTask> getUserPendingTasks(String userId) {
        return repository.findByUserIdAndStatus(userId, ScheduleTask.TaskStatus.PENDING);
    }

    @Transactional
    public void cancelAllPendingTasks(String userId) {
        List<ScheduleTask> tasks = repository.findByUserIdAndStatus(userId, ScheduleTask.TaskStatus.PENDING);
        tasks.forEach(t -> t.setStatus(ScheduleTask.TaskStatus.CANCELLED));
        repository.saveAll(tasks);
    }

    /**
     * 定時檢查並執行任務
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
                executeTask(task);
            } catch (Exception e) {
                log.error(">>>> [Schedule] 任務執行失敗 ID: {}", task.getId(), e);
                task.setStatus(ScheduleTask.TaskStatus.FAILED);
            }
        }
        repository.saveAll(tasks); // 批次更新狀態
    }

    private void executeTask(ScheduleTask task) {
        log.info(">>>> [Schedule] 執行任務 ID: {}, Type: {}", task.getId(), task.getTaskType());

        if (task.getTaskType() == ScheduleTask.TaskType.TICKET_MONITOR) {
            handleTicketMonitor(task);
        } else {
            handleReminder(task);
        }
    }

    private void handleReminder(ScheduleTask task) {
        String message = "⏰ 提醒事項：\n" + task.getContent();
        pushMessage(task.getUserId(), new TextMessage(message));
        task.setStatus(ScheduleTask.TaskStatus.EXECUTED);
    }

    private void handleTicketMonitor(ScheduleTask task) {
        try {
            SearchRequest request = JsonUtil.fromJson(task.getPayload(), SearchRequest.class);
            List<ThsrSummaryDTO> trains = ticketService.searchTickets(request);

            // 檢查是否有位子 (標準座或商務座非客滿)
            List<ThsrSummaryDTO> availableTrains = trains.stream()
                    .filter(t -> !t.standardSeatStatus().contains("客滿"))
                    .collect(Collectors.toList());

            if (!availableTrains.isEmpty()) {
                log.info(">>>> [Schedule] 監控任務 ID: {} 發現有票！發送通知並結束任務。", task.getId());
                
                // 取第一班有位子的車次
                ThsrSummaryDTO targetTrain = availableTrains.getFirst();
                
                // 產生 Deep Link
                String link = ticketService.generateDeepLink(
                        request.from(), 
                        request.to(), 
                        request.date(), 
                        targetTrain.departureTime(), 
                        targetTrain.trainNo()
                );
                
                // 產生 Booking Confirmation Flex Message
                Message flex = FlexMessageUtil.createBookingConfirmationBubble(
                        baseUrl,
                        link,
                        request.from(),
                        request.to(),
                        request.date(),
                        targetTrain.departureTime(),
                        targetTrain.trainNo()
                );
                pushMessage(task.getUserId(), flex);

                task.setStatus(ScheduleTask.TaskStatus.COMPLETED);
            } else {
                log.info(">>>> [Schedule] 監控任務 ID: {} 目前仍無票，延後 10 分鐘再試。", task.getId());
                // 延後 10 分鐘
                task.setTriggerTime(LocalDateTime.now().plusMinutes(10));
                // 狀態保持 PENDING
            }

        } catch (Exception e) {
            log.error(">>>> [Schedule] 查票失敗", e);
            // 查票失敗也延後重試，避免一直死循環
            task.setTriggerTime(LocalDateTime.now().plusMinutes(5));
        }
    }

    private void pushMessage(String userId, Message message) {
        messagingApiClient.pushMessage(
                UUID.randomUUID(),
                new PushMessageRequest.Builder(userId, List.of(message)).build()
        ).join();
    }
}

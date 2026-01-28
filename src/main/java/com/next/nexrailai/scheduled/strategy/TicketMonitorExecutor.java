package com.next.nexrailai.scheduled.strategy;

import com.linecorp.bot.messaging.model.Message;
import com.linecorp.bot.messaging.model.TextMessage;
import com.next.nexrailai.dto.ThsrSummaryDTO;
import com.next.nexrailai.dto.ai.SearchRequest;
import com.next.nexrailai.jpa.entity.ScheduleTask;
import com.next.nexrailai.service.LineMessageService;
import com.next.nexrailai.service.SystemConfigService;
import com.next.nexrailai.service.ThsrTicketService;
import com.next.nexrailai.utils.FlexMessageUtil;
import com.next.nexrailai.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class TicketMonitorExecutor implements ScheduleTaskExecutor {

    @Value("${app.base-url}")
    private String baseUrl;

    private final ThsrTicketService ticketService;
    private final LineMessageService lineMessageService;
    private final SystemConfigService systemConfigService;

    @Override
    public ScheduleTask.TaskType getSupportedTaskType() {
        return ScheduleTask.TaskType.TICKET_MONITOR;
    }

    @Override
    public void execute(ScheduleTask task) {
        try {
            SearchRequest request = JsonUtil.fromJson(task.getPayload(), SearchRequest.class);
            if (request == null) {
                log.error(">>>> [TicketMonitor] Failed to parse payload for task ID: {}", task.getId());
                task.setStatus(ScheduleTask.TaskStatus.FAILED);
                return;
            }

            // 1. 檢查是否過期
            if (isExpired(request, task)) {
                handleExpiredTask(task, request);
                return;
            }

            // 2. 執行查詢
            List<ThsrSummaryDTO> trains = ticketService.searchTickets(request);

            // 3. 檢查是否有位子 (這裡只檢查標準座是否有非 "客滿" 的狀態)
            List<ThsrSummaryDTO> availableTrains = trains.stream()
                    .filter(t -> !t.standardSeatStatus().contains("客滿"))
                    .collect(Collectors.toList());

            if (!availableTrains.isEmpty()) {
                handleTicketsFound(task, request, availableTrains.get(0));
            } else {
                handleTicketsNotFound(task);
            }

        } catch (HttpClientErrorException e) {
            handleHttpError(task, e);
        } catch (Exception e) {
            log.error(">>>> [TicketMonitor] Unexpected error for task ID: {}", task.getId(), e);
            // 發生未知錯誤，稍後重試
            task.setTriggerTime(LocalDateTime.now().plusMinutes(5));
        }
    }

    private void handleTicketsFound(ScheduleTask task, SearchRequest request, ThsrSummaryDTO targetTrain) {
        log.info(">>>> [TicketMonitor] Task ID: {} found tickets! Sending notification.", task.getId());

        String link = ticketService.generateDeepLink(
                request.from(),
                request.to(),
                request.date(),
                targetTrain.departureTime(),
                targetTrain.trainNo()
        );

        Message flex = FlexMessageUtil.createBookingConfirmationBubble(
                baseUrl,
                link,
                request.from(),
                request.to(),
                request.date(),
                targetTrain.departureTime(),
                targetTrain.trainNo()
        );

        lineMessageService.pushMessage(task.getUserId(), flex);
        task.setStatus(ScheduleTask.TaskStatus.COMPLETED);
    }

    private void handleTicketsNotFound(ScheduleTask task) {
        int intervalSeconds = systemConfigService.getInt("ticket_monitor_interval_seconds");
        if (intervalSeconds <= 0) intervalSeconds = 60;

        log.info(">>>> [TicketMonitor] Task ID: {} still no tickets. Retry in {} seconds.", task.getId(), intervalSeconds);
        task.setTriggerTime(LocalDateTime.now().plusSeconds(intervalSeconds));
    }

    private void handleExpiredTask(ScheduleTask task, SearchRequest request) {
        String timeStr = request.time() != null ? request.time() : "全天";
        String msg = String.format("🛑 監控結束通知\n\n很抱歉，直到發車時間 (%s %s) 前，系統都未能為您監控到符合條件的座位。\n\n任務已自動結束。",
                request.date(), timeStr);

        lineMessageService.pushMessage(task.getUserId(), new TextMessage(msg));
        task.setStatus(ScheduleTask.TaskStatus.EXPIRED);
    }

    private void handleHttpError(ScheduleTask task, HttpClientErrorException e) {
        String responseBody = e.getResponseBodyAsString();
        if (responseBody.contains("無提供查詢超過供應日期的資料")) {
            log.warn(">>>> [TicketMonitor] Task ID: {} query date not yet available. Retry in 1 day.", task.getId());
            task.setTriggerTime(LocalDateTime.now().plusDays(1));
        } else {
            log.error(">>>> [TicketMonitor] API Error (HTTP {}): {}", e.getStatusCode(), responseBody);
            task.setTriggerTime(LocalDateTime.now().plusMinutes(5));
        }
    }

    private boolean isExpired(SearchRequest request, ScheduleTask task) {
        String timeStr = request.time() != null ? request.time() : "23:59";
        if (timeStr.length() == 5) timeStr += ":00";

        LocalDateTime departureDateTime = LocalDateTime.parse(request.date() + "T" + timeStr);

        if (LocalDateTime.now().isAfter(departureDateTime)) {
            log.info(">>>> [TicketMonitor] Task ID: {} expired (Departure: {})", task.getId(), departureDateTime);
            return true;
        }
        return false;
    }
}

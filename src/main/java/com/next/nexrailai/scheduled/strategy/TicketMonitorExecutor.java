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

    /**
     * Identifies the schedule task type this executor handles.
     *
     * @return the ScheduleTask.TaskType handled by this executor: TICKET_MONITOR
     */
    @Override
    public ScheduleTask.TaskType getSupportedTaskType() {
        return ScheduleTask.TaskType.TICKET_MONITOR;
    }

    /**
     * Executes a TICKET_MONITOR scheduled task: parses its payload, validates expiration, queries THSR availability,
     * notifies the user and completes the task when seats are found, or schedules retries and updates task status otherwise.
     *
     * <p>Behavior:
     * - If the task payload cannot be parsed, marks the task as FAILED.
     * - If the search request is expired, notifies the user and marks the task as EXPIRED.
     * - If available seats are found, sends a booking notification to the user and marks the task as COMPLETED.
     * - If no seats are available, schedules the next check according to system configuration.
     * - Handles HTTP client errors with task-specific retry scheduling; on other unexpected errors schedules a retry in 5 minutes.</p>
     *
     * @param task the scheduled task to execute (expected to contain a SearchRequest payload for ticket monitoring)
     */
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

    /**
     * Send a booking notification to the user for the found train and mark the schedule task as completed.
     *
     * Builds a booking deep link and a Flex message using the provided search request and target train,
     * pushes the message to the task's user, and sets the task status to COMPLETED.
     *
     * @param task the schedule task being processed
     * @param request the original search request containing origin, destination, and date
     * @param targetTrain the train summary selected as having available seats
     */
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

    /**
     * Schedule the next retry for a ticket-monitoring task when no tickets are found.
     *
     * Reads the retry interval from configuration key "ticket_monitor_interval_seconds" and, if the value is
     * less than or equal to zero, uses a default of 60 seconds; then sets the task's trigger time to now plus
     * that interval.
     *
     * @param task the scheduled task to update with the next trigger time
     */
    private void handleTicketsNotFound(ScheduleTask task) {
        int intervalSeconds = systemConfigService.getInt("ticket_monitor_interval_seconds");
        if (intervalSeconds <= 0) intervalSeconds = 60;

        log.info(">>>> [TicketMonitor] Task ID: {} still no tickets. Retry in {} seconds.", task.getId(), intervalSeconds);
        task.setTriggerTime(LocalDateTime.now().plusSeconds(intervalSeconds));
    }

    /**
     * Notifies the user that monitoring has ended without finding matching seats and marks the task as expired.
     *
     * Sends a text message describing the monitored date/time that produced no results, and sets the task status to EXPIRED.
     *
     * @param task    the scheduled task to update (user ID is used to send the notification)
     * @param request the original search request containing the date and optional time that was monitored
     */
    private void handleExpiredTask(ScheduleTask task, SearchRequest request) {
        String timeStr = request.time() != null ? request.time() : "全天";
        String msg = String.format("🛑 監控結束通知\n\n很抱歉，直到發車時間 (%s %s) 前，系統都未能為您監控到符合條件的座位。\n\n任務已自動結束。",
                request.date(), timeStr);

        lineMessageService.pushMessage(task.getUserId(), new TextMessage(msg));
        task.setStatus(ScheduleTask.TaskStatus.EXPIRED);
    }

    /**
     * Schedule the next retry for a ticket-monitor task based on an HTTP client error response.
     *
     * If the response body contains "無提供查詢超過供應日期的資料", sets the task's trigger time to one day from now; otherwise sets it to five minutes from now.
     *
     * @param task the schedule task to update with the new trigger time
     * @param e the HttpClientErrorException received from the ticket API
     */
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

    /**
     * Determines whether the monitored departure date and time from the search request has already passed.
     *
     * If `request.time()` is null, "23:59" is used; if the time string has the form "HH:mm" a ":00" suffix is appended.
     *
     * @param request the search request containing `date` and optional `time`
     * @param task the scheduled task being evaluated (used for logging)
     * @return `true` if the current time is after the computed departure date-time, `false` otherwise
     */
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
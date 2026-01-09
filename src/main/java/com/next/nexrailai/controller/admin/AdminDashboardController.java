package com.next.nexrailai.controller.admin;

import com.linecorp.bot.client.base.Result;
import com.linecorp.bot.messaging.client.MessagingApiClient;
import com.linecorp.bot.messaging.model.MessageQuotaResponse;
import com.linecorp.bot.messaging.model.QuotaConsumptionResponse;
import com.next.nexrailai.dto.admin.DashboardStatsResponse;
import com.next.nexrailai.dto.admin.LineUsageResponse;
import com.next.nexrailai.jpa.entity.ScheduleTask;
import com.next.nexrailai.jpa.repository.AppUserRepository;
import com.next.nexrailai.jpa.repository.ScheduleTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@Slf4j
public class AdminDashboardController {

    private final AppUserRepository userRepository;
    private final ScheduleTaskRepository taskRepository;
    private final MessagingApiClient messagingApiClient;

    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsResponse> getStats() {
        long totalUsers = userRepository.count();
        long activeTasks = taskRepository.countByStatus(ScheduleTask.TaskStatus.PENDING);
        long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();

        Duration duration = Duration.ofMillis(uptimeMillis);
        String uptime = String.format("%d天 %02d:%02d:%02d",
                duration.toDays(),
                duration.toHoursPart(),
                duration.toMinutesPart(),
                duration.toSecondsPart());

        return ResponseEntity.ok(new DashboardStatsResponse(
                totalUsers,
                activeTasks,
                0, // todayMessages (暫時無)
                uptime
        ));
    }

    @GetMapping("/line-usage")
    public ResponseEntity<LineUsageResponse> getLineUsage() {
        try {
            // 並行呼叫兩個 LINE API
            CompletableFuture<Result<MessageQuotaResponse>> quotaFuture = messagingApiClient.getMessageQuota();
            CompletableFuture<Result<QuotaConsumptionResponse>> consumptionFuture = messagingApiClient.getMessageQuotaConsumption();

            CompletableFuture.allOf(quotaFuture, consumptionFuture).join();

            MessageQuotaResponse quota = quotaFuture.get().body();
            QuotaConsumptionResponse consumption = consumptionFuture.get().body();

            return ResponseEntity.ok(new LineUsageResponse(
                    quota.type().name(),
                    quota.value(),
                    consumption.totalUsage()
            ));
        } catch (Exception e) {
            log.error(">>>> [Admin] Failed to get LINE usage", e);
            // 如果失敗 (例如免費版可能查不到 total quota?)，回傳預設值或錯誤
            return ResponseEntity.internalServerError().build();
        }
    }
}

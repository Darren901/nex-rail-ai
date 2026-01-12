package com.next.nexrailai.controller.admin;

import com.linecorp.bot.client.base.Result;
import com.linecorp.bot.messaging.client.MessagingApiClient;
import com.linecorp.bot.messaging.model.MessageQuotaResponse;
import com.linecorp.bot.messaging.model.NumberOfMessagesResponse;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
                uptime
        ));
    }

    @GetMapping("/line-usage")
    public ResponseEntity<LineUsageResponse> getLineUsage() {
        try {
            // 1. 並行呼叫額度相關 API (即時資料)
            CompletableFuture<Result<MessageQuotaResponse>> quotaFuture = messagingApiClient.getMessageQuota();
            CompletableFuture<Result<QuotaConsumptionResponse>> consumptionFuture = messagingApiClient.getMessageQuotaConsumption();

            // 2. 尋找最近的統計資料
            // 從昨天開始，最多往前找 3 天
            NumberOfMessagesResponse validStats = null;
            String statsDate = null;

            for (int i = 1; i <= 3; i++) {
                LocalDate targetDate = LocalDate.now().minusDays(i);
                String dateStr = targetDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                
                try {
                    NumberOfMessagesResponse response = messagingApiClient
                            .getNumberOfSentReplyMessages(dateStr)
                            .join()
                            .body();

                    // 檢查狀態 (LINE API 回應包含 status: "ready" / "unready" / "out_of_service")
                    // 如果成功取得且不是 unready，就使用它
                    if (response != null && "ready".equalsIgnoreCase(response.status().toString())) {
                        validStats = response;
                        statsDate = targetDate.toString();
                        log.info(">>>> [Admin] Found valid LINE stats for date: {}", statsDate);
                        break;
                    } else {
                        log.warn(">>>> [Admin] LINE stats not ready for date: {}", dateStr);
                    }
                } catch (Exception e) {
                    log.warn(">>>> [Admin] Failed to fetch stats for {}: {}", dateStr, e.getMessage());
                }
            }
            
            // 如果都找不到，就給預設值
            long replyCount = (validStats != null && validStats.success() != null) ? validStats.success() : 0;
            if (statsDate == null) statsDate = "N/A";

            CompletableFuture.allOf(quotaFuture, consumptionFuture).join();
            MessageQuotaResponse quota = quotaFuture.get().body();
            QuotaConsumptionResponse consumption = consumptionFuture.get().body();

            return ResponseEntity.ok(new LineUsageResponse(
                    quota.type().name(),
                    quota.value(),
                    consumption.totalUsage(),
                    replyCount,
                    statsDate
            ));
        } catch (Exception e) {
            log.error(">>>> [Admin] Failed to get LINE usage", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

package com.next.nexrailai.scheduled;

import com.linecorp.bot.messaging.model.TextMessage;
import com.next.nexrailai.aspect.DistributedLock;
import com.next.nexrailai.config.StockProperties;
import com.next.nexrailai.service.LineMessageService;
import com.next.nexrailai.service.StockAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class StockScheduler {

    private final StockAiService stockAiService;
    private final LineMessageService lineMessageService;
    private final StockProperties stockProperties;

    // 每日 08:00 台灣時間（週一至週五）
    @Scheduled(cron = "0 0 8 * * TUE-SAT", zone = "Asia/Taipei")
    @DistributedLock(key = "stock-daily-report")
    public void sendDailyReport() {
        log.info(">>>> [Stock Scheduler] 開始產生每日美股報告");
        try {
            String reportUrl = stockAiService.generateAndSaveReport();
            lineMessageService.pushMessage(
                stockProperties.ownerLineUserId(),
                new TextMessage("📊 今日美股投資分析報告已出爐！\n\n點此查看完整分析：\n" + reportUrl)
            );
            log.info(">>>> [Stock Scheduler] 每日美股報告已發送: {}", reportUrl);
        } catch (Exception e) {
            log.error(">>>> [Stock Scheduler] 發送每日報告失敗", e);
        }
    }
}

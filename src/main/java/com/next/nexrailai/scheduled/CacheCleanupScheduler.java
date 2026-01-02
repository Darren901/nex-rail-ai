package com.next.nexrailai.scheduled;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Set;

@Component
@Slf4j
@RequiredArgsConstructor
public class CacheCleanupScheduler {

    private final StringRedisTemplate redisTemplate;

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
}

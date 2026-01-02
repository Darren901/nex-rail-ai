package com.next.nexrailai.scheduled;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Slf4j
@RequiredArgsConstructor
public class CacheMonitor {

    private final StringRedisTemplate redisTemplate;

    @Scheduled(fixedRate = 300000)  // 每 5 分鐘
    public void logCacheStats() {
        try {
            // 統計不同類型的 cache key 數量
            Set<String> timetableKeys = redisTemplate.keys("tdx:timetable:*");
            Set<String> fareKeys = redisTemplate.keys("tdx:fares:*");

            log.info("===== Cache 統計 =====");
            log.info("時刻表 Cache 數量: {}", timetableKeys != null ? timetableKeys.size() : 0);
            log.info("票價 Cache 數量: {}", fareKeys != null ? fareKeys.size() : 0);
            log.info("=====================");
        } catch (Exception e) {
            log.error("Cache 統計失敗", e);
        }
    }
}

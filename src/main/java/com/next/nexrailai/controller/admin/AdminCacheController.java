package com.next.nexrailai.controller.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/cache")
@RequiredArgsConstructor
@Slf4j
public class AdminCacheController {

    private final StringRedisTemplate redisTemplate;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        
        long dailyQuota = countKeys("rate_limit:quota:*");
        long monthlyNotify = countKeys("rate_limit:monthly_notify:*");
        long token = countKeys("tdx:access_token");
        long timetable = countKeys("tdx:timetable:*");
        long fares = countKeys("tdx:fares:*");
        long config = countKeys("system:config:*");

        stats.put("dailyQuotaKeys", dailyQuota);
        stats.put("monthlyNotifyKeys", monthlyNotify);
        stats.put("tokenKeys", token);
        stats.put("timetableKeys", timetable);
        stats.put("fareKeys", fares);
        stats.put("configKeys", config);
        stats.put("totalKeys", dailyQuota + monthlyNotify + token + timetable + fares + config);
        
        return ResponseEntity.ok(stats);
    }
    
    private long countKeys(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        return keys != null ? keys.size() : 0;
    }

    /**
     * 統一清理介面
     */
    @DeleteMapping("/clear/{type}")
    public ResponseEntity<Map<String, String>> clearCache(@PathVariable String type) {
        String pattern = switch (type.toUpperCase()) {
            case "ALL" -> "*";
            case "TOKEN" -> "tdx:access_token";
            case "TIMETABLE" -> "tdx:timetable:*";
            case "FARES" -> "tdx:fares:*";
            case "DAILY_QUOTA" -> "rate_limit:quota:*";
            case "MONTHLY_NOTIFY" -> "rate_limit:monthly_notify:*";
            case "CONFIG" -> "system:config:*";
            default -> null;
        };

        if (pattern == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid cache type"));
        }

        try {
            Set<String> keys = redisTemplate.keys(pattern);
            int count = 0;
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                count = keys.size();
                log.info(">>>> [Admin Cache] Cleared type: {}, count: {}", type, count);
            }
            return ResponseEntity.ok(Map.of("message", "已清除 " + count + " 筆 " + type + " 快取"));
        } catch (Exception e) {
            log.error(">>>> [Admin Cache] 清除失敗", e);
            return ResponseEntity.internalServerError().body(Map.of("message", "清除失敗: " + e.getMessage()));
        }
    }
}
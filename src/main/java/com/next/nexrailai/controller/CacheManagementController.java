package com.next.nexrailai.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/admin/cache")
@RequiredArgsConstructor
@Slf4j
public class CacheManagementController {

    private final StringRedisTemplate redisTemplate;

    @DeleteMapping("/token")
    public ResponseEntity<String> clearAccessTokenCache() {
        try {
            Set<String> keys = redisTemplate.keys("tdx:access_token");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info(">>>> [Cache Manager] 已清除{} 個 accessToken", keys.size());
                return ResponseEntity.ok("已清除 " + keys.size() + " 個 accessToken");
            }
            return ResponseEntity.ok("沒有 accessToken cache 需要清除");
        } catch (Exception e) {
            log.error(">>>> [Cache Manager] 清除 accessToken cache 失敗", e);
            return ResponseEntity.status(500).body("清除失敗: " + e.getMessage());
        }
    }

    // 清除所有時刻表 cache (高鐵改點時使用)
    @DeleteMapping("/timetable")
    public ResponseEntity<String> clearTimetableCache() {
        try {
            Set<String> keys = redisTemplate.keys("tdx:timetable:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info(">>>> [Cache Manager] 已清除 {} 個時刻表 cache", keys.size());
                return ResponseEntity.ok("已清除 " + keys.size() + " 個時刻表 cache");
            }
            return ResponseEntity.ok("沒有時刻表 cache 需要清除");
        } catch (Exception e) {
            log.error(">>>> [Cache Manager] 清除時刻表 cache 失敗", e);
            return ResponseEntity.status(500).body("清除失敗: " + e.getMessage());
        }
    }

    // 清除所有票價 cache
    @DeleteMapping("/fares")
    public ResponseEntity<String> clearFareCache() {
        try {
            Set<String> keys = redisTemplate.keys("tdx:fares:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info(">>>> [Cache Manager] 已清除 {} 個票價 cache", keys.size());
                return ResponseEntity.ok("已清除 " + keys.size() + " 個票價 cache");
            }
            return ResponseEntity.ok("沒有票價 cache 需要清除");
        } catch (Exception e) {
            log.error(">>>> [Cache Manager] 清除票價 cache 失敗", e);
            return ResponseEntity.status(500).body("清除失敗: " + e.getMessage());
        }
    }
}

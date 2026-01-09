package com.next.nexrailai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;
    
    private static final int DAILY_QUOTA = 10;
    private static final String KEY_PREFIX = "rate_limit:quota:";
    
    private static final int MONTHLY_NOTIFICATION_QUOTA = 5;
    private static final String MONTHLY_KEY_PREFIX = "rate_limit:monthly_notify:";

    /**
     * 檢查並扣除一般 AI 對話額度 (每日)
     * @return true 如果還有額度 (扣除成功), false 如果額度已用完
     */
    public boolean tryConsume(String userId) {
        String key = getKey(userId);
        
        // 1. 如果 Key 不存在，初始化為 DAILY_QUOTA，並設定過期時間 (到明天 00:00)
        redisTemplate.opsForValue().setIfAbsent(key, String.valueOf(DAILY_QUOTA), Duration.ofDays(1));
        
        // 2. 執行 DECR
        Long remaining = redisTemplate.opsForValue().decrement(key);
        
        if (remaining != null && remaining >= 0) {
            log.debug(">>>> [Rate Limit] User {} consumed 1 daily quota. Remaining: {}", userId, remaining);
            return true;
        } else {
            redisTemplate.opsForValue().increment(key);
            log.info(">>>> [Rate Limit] User {} daily quota exceeded.", userId);
            return false;
        }
    }

    /**
     * 檢查並扣除主動推播額度 (每月)
     * 用於待辦提醒或搶票監控通知
     * @return true 如果還有額度 (扣除成功), false 如果額度已用完
     */
    public boolean tryConsumeMonthlyNotification(String userId) {
        String key = getMonthlyKey(userId);

        // 初始化每月額度，設定過期時間 32 天 (確保跨月自動過期)
        redisTemplate.opsForValue().setIfAbsent(key, String.valueOf(MONTHLY_NOTIFICATION_QUOTA), Duration.ofDays(32));

        Long remaining = redisTemplate.opsForValue().decrement(key);

        if (remaining != null && remaining >= 0) {
            log.debug(">>>> [Rate Limit] User {} consumed 1 monthly notification quota. Remaining: {}", userId, remaining);
            return true;
        } else {
            redisTemplate.opsForValue().increment(key);
            log.info(">>>> [Rate Limit] User {} monthly notification quota exceeded.", userId);
            return false;
        }
    }
    
    /**
     * 查詢剩餘額度
     */
    public int getRemainingQuota(String userId) {
        String key = getKey(userId);
        String val = redisTemplate.opsForValue().get(key);
        if (val == null) {
            return DAILY_QUOTA;
        }
        return Math.max(0, Integer.parseInt(val));
    }

    /**
     * 查詢每月剩餘額度 (提醒/監控)
     */
    public int getRemainingMonthlyQuota(String userId) {
        String key = getMonthlyKey(userId);
        String val = redisTemplate.opsForValue().get(key);
        if (val == null) {
            // 如果從未設定過，預設就是滿額
            return MONTHLY_NOTIFICATION_QUOTA;
        }
        return Math.max(0, Integer.parseInt(val));
    }
    
    /**
     * (後門) 增加每日額度
     */
    public void addQuota(String userId, int amount) {
        String key = getKey(userId);
        // 如果 key 不存在，先初始化再增加
        redisTemplate.opsForValue().setIfAbsent(key, String.valueOf(DAILY_QUOTA), Duration.ofDays(1));
        redisTemplate.opsForValue().increment(key, amount);
    }

    /**
     * (後門) 設定每日額度
     */
    public void setQuota(String userId, int amount) {
        String key = getKey(userId);
        redisTemplate.opsForValue().set(key, String.valueOf(amount), Duration.ofDays(1));
    }

    /**
     * (後門) 增加每月額度
     */
    public void addMonthlyQuota(String userId, int amount) {
        String key = getMonthlyKey(userId);
        redisTemplate.opsForValue().setIfAbsent(key, String.valueOf(MONTHLY_NOTIFICATION_QUOTA), Duration.ofDays(32));
        redisTemplate.opsForValue().increment(key, amount);
    }

    /**
     * (後門) 設定每月額度
     */
    public void setMonthlyQuota(String userId, int amount) {
        String key = getMonthlyKey(userId);
        redisTemplate.opsForValue().set(key, String.valueOf(amount), Duration.ofDays(32));
    }

    private String getKey(String userId) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE); // yyyyMMdd
        return KEY_PREFIX + userId + ":" + date;
    }

    private String getMonthlyKey(String userId) {
        String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        return MONTHLY_KEY_PREFIX + userId + ":" + month;
    }
}

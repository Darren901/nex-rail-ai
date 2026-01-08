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

    /**
     * 檢查並扣除額度
     * @return true 如果還有額度 (扣除成功), false 如果額度已用完
     */
    public boolean tryConsume(String userId) {
        String key = getKey(userId);
        
        // 1. 如果 Key 不存在，初始化為 DAILY_QUOTA，並設定過期時間 (到明天 00:00)
        // 使用 setIfAbsent (SETNX) 確保併發安全
        Boolean isSet = redisTemplate.opsForValue().setIfAbsent(key, String.valueOf(DAILY_QUOTA), Duration.ofDays(1));
        
        // 如果是新的一天(或新用戶)，剛剛被初始化了，不需要再扣嗎？
        // 不，初始化只是設定起跑點。現在要扣這一次的使用。
        // 但是如果剛初始化，值是 10。
        
        // 2. 執行 DECR
        Long remaining = redisTemplate.opsForValue().decrement(key);
        
        if (remaining != null && remaining >= 0) {
            log.debug("User {} consumed 1 quota. Remaining: {}", userId, remaining);
            return true;
        } else {
            log.info("User {} quota exceeded.", userId);
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
        return Integer.parseInt(val);
    }
    
    /**
     * (後門) 增加額度
     */
    public void addQuota(String userId, int amount) {
        String key = getKey(userId);
        redisTemplate.opsForValue().increment(key, amount);
    }

    private String getKey(String userId) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE); // yyyyMMdd
        return KEY_PREFIX + userId + ":" + date;
    }
}

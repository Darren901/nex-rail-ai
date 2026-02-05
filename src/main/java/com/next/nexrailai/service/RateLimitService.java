package com.next.nexrailai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class RateLimitService {

    private final RedissonClient redissonClient;
    private final SystemConfigService systemConfigService;
    
    private static final String KEY_PREFIX = "rate_limit:quota:";
    private static final String MONTHLY_KEY_PREFIX = "rate_limit:monthly_notify:";

    /**
     * 檢查並扣除一般 AI 對話額度 (每日)
     * 使用 Redisson RRateLimiter 保證原子性
     * @return true 如果還有額度 (扣除成功), false 如果額度已用完
     */
    public boolean tryConsume(String userId) {
        String key = getKey(userId);
        int dailyQuota = systemConfigService.getInt("daily_message_limit");
        
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        
        // 如果尚未初始化，則設定速率限制：每日 dailyQuota 個令牌
        if (!rateLimiter.isExists()) {
            rateLimiter.trySetRate(RateType.OVERALL, dailyQuota, 1, RateIntervalUnit.DAYS);
        }
        
        // 嘗試獲取 1 個令牌 (原子操作)
        boolean acquired = rateLimiter.tryAcquire(1);
        
        if (acquired) {
            long remaining = rateLimiter.availablePermits();
            log.debug(">>>> [Rate Limit] User {} consumed 1 daily quota. Remaining: {}", userId, remaining);
        } else {
            log.info(">>>> [Rate Limit] User {} daily quota exceeded.", userId);
        }
        
        return acquired;
    }

    /**
     * 檢查並扣除主動推播額度 (每月)
     * 用於待辦提醒或搶票監控通知
     * 使用 Redisson RRateLimiter 保證原子性
     * @return true 如果還有額度 (扣除成功), false 如果額度已用完
     */
    public boolean tryConsumeMonthlyNotification(String userId) {
        String key = getMonthlyKey(userId);
        int monthlyQuota = systemConfigService.getInt("monthly_broadcast_limit");

        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        
        // 如果尚未初始化，則設定速率限制：每 32 天 monthlyQuota 個令牌 (確保跨月自動過期)
        if (!rateLimiter.isExists()) {
            rateLimiter.trySetRate(RateType.OVERALL, monthlyQuota, 32, RateIntervalUnit.DAYS);
        }

        // 嘗試獲取 1 個令牌 (原子操作)
        boolean acquired = rateLimiter.tryAcquire(1);

        if (acquired) {
            long remaining = rateLimiter.availablePermits();
            log.debug(">>>> [Rate Limit] User {} consumed 1 monthly notification quota. Remaining: {}", userId, remaining);
        } else {
            log.info(">>>> [Rate Limit] User {} monthly notification quota exceeded.", userId);
        }

        return acquired;
    }
    
    /**
     * 查詢剩餘額度
     */
    public int getRemainingQuota(String userId) {
        String key = getKey(userId);
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        
        if (!rateLimiter.isExists()) {
            // 如果從未設定過，預設就是滿額
            return systemConfigService.getInt("daily_message_limit");
        }
        
        return (int) Math.max(0, rateLimiter.availablePermits());
    }

    /**
     * 查詢每月剩餘額度 (提醒/監控)
     */
    public int getRemainingMonthlyQuota(String userId) {
        String key = getMonthlyKey(userId);
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        
        if (!rateLimiter.isExists()) {
            // 如果從未設定過，預設就是滿額
            return systemConfigService.getInt("monthly_broadcast_limit");
        }
        
        return (int) Math.max(0, rateLimiter.availablePermits());
    }
    
    /**
     * 增加每日額度
     * RRateLimiter 不支援動態增加令牌，此方法會刪除舊限流器並重新設定速率
     */
    public void addQuota(String userId, int amount) {
        String key = getKey(userId);
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        
        int currentRemaining = (int) rateLimiter.availablePermits();
        int newTotal = currentRemaining + amount;
        
        // 刪除舊的限流器並重新建立（因為 trySetRate 對已存在的限流器無效）
        rateLimiter.delete();
        rateLimiter.trySetRate(RateType.OVERALL, newTotal, 1, RateIntervalUnit.DAYS);
        
        log.info(">>>> [Rate Limit] User {} added {} daily quota. New total: {}", userId, amount, newTotal);
    }

    /**
     * 設定每日額度
     */
    public void setQuota(String userId, int amount) {
        String key = getKey(userId);
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        
        // 刪除舊的限流器並重新建立
        rateLimiter.delete();
        rateLimiter.trySetRate(RateType.OVERALL, amount, 1, RateIntervalUnit.DAYS);
        
        log.info(">>>> [Rate Limit] User {} daily quota set to {}", userId, amount);
    }

    /**
     * 增加每月額度
     * RRateLimiter 不支援動態增加令牌，此方法會刪除舊限流器並重新設定速率
     */
    public void addMonthlyQuota(String userId, int amount) {
        String key = getMonthlyKey(userId);
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        
        int currentRemaining = (int) rateLimiter.availablePermits();
        int newTotal = currentRemaining + amount;
        
        // 刪除舊的限流器並重新建立（因為 trySetRate 對已存在的限流器無效）
        rateLimiter.delete();
        rateLimiter.trySetRate(RateType.OVERALL, newTotal, 32, RateIntervalUnit.DAYS);
        
        log.info(">>>> [Rate Limit] User {} added {} monthly quota. New total: {}", userId, amount, newTotal);
    }

    /**
     * 設定每月額度
     */
    public void setMonthlyQuota(String userId, int amount) {
        String key = getMonthlyKey(userId);
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        
        // 刪除舊的限流器並重新建立
        rateLimiter.delete();
        rateLimiter.trySetRate(RateType.OVERALL, amount, 32, RateIntervalUnit.DAYS);
        
        log.info(">>>> [Rate Limit] User {} monthly quota set to {}", userId, amount);
    }

    /**
     * 每日額度的 Key (純令牌桶模式 - 不包含日期)
     * Key 固定不變，令牌持續補充，可跨日累積
     */
    private String getKey(String userId) {
        return KEY_PREFIX + userId;
    }

    /**
     * 每月額度的 Key (純令牌桶模式 - 不包含月份)
     * Key 固定不變，令牌持續補充，可跨月累積
     */
    private String getMonthlyKey(String userId) {
        return MONTHLY_KEY_PREFIX + userId;
    }
}

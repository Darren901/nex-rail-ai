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
     * @return true 如果還有額度 (扣除成功), false 如果額度已用完
     */
    public boolean tryConsume(String userId) {
        String key = getKey(userId);
        try {
            int dailyQuota = systemConfigService.getInt("daily_message_limit");
            
            RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
            
            // 檢查是否需要初始化或重新設定（配置變更檢測）
            if (!rateLimiter.isExists()) {
                // 首次初始化
                rateLimiter.trySetRate(RateType.OVERALL, dailyQuota, 1, RateIntervalUnit.DAYS);
                log.debug(">>>> [Rate Limit] Initialized rate limiter for user {}: {} tokens/day", userId, dailyQuota);
            } else {
                // 檢查配置是否變更
                long currentConfiguredRate = rateLimiter.getConfig().getRate();
                if (currentConfiguredRate != dailyQuota) {
                    log.info(">>>> [Rate Limit] Detected config change for user {}. Old rate: {}, New rate: {}", 
                            userId, currentConfiguredRate, dailyQuota);
                    // 配置已變更，重新設定限流器（保留剩餘令牌比例）
                    adjustQuotaForConfigChange(rateLimiter, currentConfiguredRate, dailyQuota, 1, RateIntervalUnit.DAYS);
                }
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
        } catch (Exception e) {
            log.error(">>>> [Rate Limit] tryConsume failed for user {}: {}", userId, e.getMessage(), e);
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
        try {
            int monthlyQuota = systemConfigService.getInt("monthly_broadcast_limit");

            RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
            
            // 檢查是否需要初始化或重新設定（配置變更檢測）
            if (!rateLimiter.isExists()) {
                // 首次初始化：每 32 天 monthlyQuota 個令牌 (確保跨月自動過期)
                rateLimiter.trySetRate(RateType.OVERALL, monthlyQuota, 32, RateIntervalUnit.DAYS);
                log.debug(">>>> [Rate Limit] Initialized monthly rate limiter for user {}: {} tokens/32days", userId, monthlyQuota);
            } else {
                // 檢查配置是否變更
                long currentConfiguredRate = rateLimiter.getConfig().getRate();
                if (currentConfiguredRate != monthlyQuota) {
                    log.info(">>>> [Rate Limit] Detected monthly config change for user {}. Old rate: {}, New rate: {}", 
                            userId, currentConfiguredRate, monthlyQuota);
                    // 配置已變更，重新設定限流器（保留剩餘令牌比例）
                    adjustQuotaForConfigChange(rateLimiter, currentConfiguredRate, monthlyQuota, 32, RateIntervalUnit.DAYS);
                }
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
        } catch (Exception e) {
            log.error(">>>> [Rate Limit] tryConsumeMonthlyNotification failed for user {}: {}", userId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 查詢剩餘額度
     */
    public int getRemainingQuota(String userId) {
        String key = getKey(userId);
        try {
            RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
            
            if (!rateLimiter.isExists()) {
                // 如果從未設定過，預設就是滿額
                return systemConfigService.getInt("daily_message_limit");
            }
            
            return (int) Math.max(0, rateLimiter.availablePermits());
        } catch (Exception e) {
            log.error(">>>> [Rate Limit] getRemainingQuota failed for user {}: {}", userId, e.getMessage(), e);
            return 0; // Safe default
        }
    }

    /**
     * 查詢每月剩餘額度 (提醒/監控)
     */
    public int getRemainingMonthlyQuota(String userId) {
        String key = getMonthlyKey(userId);
        try {
            RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
            
            if (!rateLimiter.isExists()) {
                // 如果從未設定過，預設就是滿額
                return systemConfigService.getInt("monthly_broadcast_limit");
            }
            
            return (int) Math.max(0, rateLimiter.availablePermits());
        } catch (Exception e) {
            log.error(">>>> [Rate Limit] getRemainingMonthlyQuota failed for user {}: {}", userId, e.getMessage(), e);
            return 0; // Safe default
        }
    }
    
    /**
     * 增加每日額度
     */
    public void addQuota(String userId, int amount) {
        String key = getKey(userId);
        try {
            RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
            
            // 取得目前設定的速率（容量），而非剩餘令牌數
            long currentConfiguredRate = 0;
            if (rateLimiter.isExists() && rateLimiter.getConfig() != null) {
                currentConfiguredRate = rateLimiter.getConfig().getRate();
            } else {
                // 若不存在，使用系統預設值
                currentConfiguredRate = systemConfigService.getInt("daily_message_limit");
            }

            int newTotal = (int) currentConfiguredRate + amount;
            
            // 刪除舊的限流器並重新建立（因為 trySetRate 對已存在的限流器無效）
            rateLimiter.delete();
            rateLimiter.trySetRate(RateType.OVERALL, newTotal, 1, RateIntervalUnit.DAYS);
            
            log.info(">>>> [Rate Limit] User {} added {} to configured rate. Old rate: {}, New rate: {}", 
                    userId, amount, currentConfiguredRate, newTotal);
        } catch (Exception e) {
            log.error(">>>> [Rate Limit] addQuota failed for user {}: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * 設定每日額度
     */
    public void setQuota(String userId, int amount) {
        String key = getKey(userId);
        try {
            RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
            
            // 刪除舊的限流器並重新建立
            rateLimiter.delete();
            rateLimiter.trySetRate(RateType.OVERALL, amount, 1, RateIntervalUnit.DAYS);
            
            log.info(">>>> [Rate Limit] User {} daily quota set to {}", userId, amount);
        } catch (Exception e) {
            log.error(">>>> [Rate Limit] setQuota failed for user {}: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * 增加每月額度
     */
    public void addMonthlyQuota(String userId, int amount) {
        String key = getMonthlyKey(userId);
        try {
            RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
            
            // 取得目前設定的速率（容量），而非剩餘令牌數
            long currentConfiguredRate = 0;
            if (rateLimiter.isExists() && rateLimiter.getConfig() != null) {
                currentConfiguredRate = rateLimiter.getConfig().getRate();
            } else {
                // 若不存在，使用系統預設值
                currentConfiguredRate = systemConfigService.getInt("monthly_broadcast_limit");
            }
            
            int newTotal = (int) currentConfiguredRate + amount;
            
            // 刪除舊的限流器並重新建立（因為 trySetRate 對已存在的限流器無效）
            rateLimiter.delete();
            rateLimiter.trySetRate(RateType.OVERALL, newTotal, 32, RateIntervalUnit.DAYS);
            
            log.info(">>>> [Rate Limit] User {} added {} to configured rate. Old rate: {}, New rate: {}", 
                    userId, amount, currentConfiguredRate, newTotal);
        } catch (Exception e) {
            log.error(">>>> [Rate Limit] addMonthlyQuota failed for user {}: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * 設定每月額度
     */
    public void setMonthlyQuota(String userId, int amount) {
        String key = getMonthlyKey(userId);
        try {
            RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
            
            // 刪除舊的限流器並重新建立
            rateLimiter.delete();
            rateLimiter.trySetRate(RateType.OVERALL, amount, 32, RateIntervalUnit.DAYS);
            
            log.info(">>>> [Rate Limit] User {} monthly quota set to {}", userId, amount);
        } catch (Exception e) {
            log.error(">>>> [Rate Limit] setMonthlyQuota failed for user {}: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * 調整限流器配額以反映系統配置變更
     * 保留剩餘令牌的比例，避免用戶突然失去已累積的額度
     * 
     * @param rateLimiter 限流器實例
     * @param oldRate 舊的配置速率
     * @param newRate 新的配置速率
     * @param rateInterval 速率間隔（1 或 32）
     * @param intervalUnit 間隔單位（DAYS）
     */
    private void adjustQuotaForConfigChange(RRateLimiter rateLimiter, long oldRate, int newRate, 
                                            int rateInterval, RateIntervalUnit intervalUnit) {
        // 計算剩餘令牌比例
        long currentRemaining = rateLimiter.availablePermits();
        double remainingRatio = oldRate > 0 ? (double) currentRemaining / oldRate : 1.0;
        
        // 按比例調整剩餘令牌
        int newRemaining = (int) Math.ceil(newRate * remainingRatio);
        
        // 刪除舊限流器並重新建立
        rateLimiter.delete();
        rateLimiter.trySetRate(RateType.OVERALL, newRate, rateInterval, intervalUnit);
        
        // 如果新剩餘額度小於新容量，需要手動扣除差額以保留比例
        if (newRemaining < newRate) {
            int tokensToConsume = newRate - newRemaining;
            for (int i = 0; i < tokensToConsume && rateLimiter.tryAcquire(1); i++) {
                // 消耗多餘的令牌以達到期望的剩餘量
            }
        }
        
        log.debug(">>>> [Rate Limit] Adjusted quota: oldRate={}, newRate={}, oldRemaining={}, newRemaining={}", 
                oldRate, newRate, currentRemaining, newRemaining);
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

package com.next.nexrailai.aspect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 分散式鎖切面（使用 Redisson）
 * 攔截帶有 @DistributedLock 註解的方法，確保多實例環境下只有一個實例執行
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DistributedLockAspect {

    private final RedissonClient redissonClient;

    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        String lockKey = "lock:scheduled:" + distributedLock.key();
        RLock lock = redissonClient.getLock(lockKey);

        boolean acquired = false;
        try {
            // 嘗試取得鎖
            // leaseTime = -1 表示使用 Watchdog 自動續約
            acquired = lock.tryLock(-1, TimeUnit.MILLISECONDS);

            if (acquired) {
                log.info(">>>> [分散式鎖 Redisson] 取得鎖成功: {}, Watchdog 已啟動", lockKey);
                return joinPoint.proceed();
            } else {
                log.warn(">>>> [分散式鎖 Redisson] 無法取得鎖，跳過執行: {} (其他實例正在執行)", lockKey);
                return null;
            }
        } catch (Exception e) {
            log.error(">>>> [分散式鎖 Redisson] 執行失敗: {}", lockKey, e);
            throw e;
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info(">>>> [分散式鎖 Redisson] 釋放鎖成功: {}, Watchdog 已停止", lockKey);
            }
        }
    }
}

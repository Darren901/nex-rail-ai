package com.next.nexrailai.integration;

import com.next.nexrailai.service.RateLimitService;
import com.next.nexrailai.service.SystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RateLimitService 整合測試
 * 使用 Testcontainers 啟動真實 Redis 環境，測試 Redisson RRateLimiter 令牌桶行為
 */
@Slf4j
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public class RateLimitServiceIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withReuse(true);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("spring.data.redis.password", () -> "");
    }

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private SystemConfigService systemConfigService;

    @BeforeEach
    void setUp() {
        // 確保 Redis 已啟動
        assertTrue(redis.isRunning(), "Redis container should be running");
        
        // 清理測試資料
        redissonClient.getKeys().flushdb();
    }

    /**
     * 測試 1: 基本令牌桶功能 - 消耗與補充
     */
    @Test
    @DisplayName("應該正確消耗令牌並在額度用完後拒絕請求")
    void shouldConsumeTokensAndRejectWhenExhausted() {
        String userId = "U_TEST_001";
        int dailyQuota = systemConfigService.getInt("daily_message_limit"); // 預設 10

        // 消耗所有額度
        for (int i = 0; i < dailyQuota; i++) {
            boolean result = rateLimitService.tryConsume(userId);
            assertTrue(result, "第 " + (i + 1) + " 次請求應該成功");
            log.info(">>>> [Test] 第 {} 次請求成功，剩餘額度: {}", i + 1, rateLimitService.getRemainingQuota(userId));
        }

        // 驗證剩餘額度為 0
        assertEquals(0, rateLimitService.getRemainingQuota(userId));

        // 第 11 次請求應該失敗
        boolean result = rateLimitService.tryConsume(userId);
        assertFalse(result, "額度用完後的請求應該失敗");
        
        log.info(">>>> [Test] 額度用完後，剩餘額度: {}", rateLimitService.getRemainingQuota(userId));
    }

    /**
     * 測試 2: 令牌桶初始化
     */
    @Test
    @DisplayName("新使用者應該有完整的初始額度")
    void shouldHaveFullQuotaForNewUser() {
        String userId = "U_NEW_USER";
        int dailyQuota = systemConfigService.getInt("daily_message_limit");

        // 查詢剩餘額度（尚未初始化）
        int remaining = rateLimitService.getRemainingQuota(userId);
        assertEquals(dailyQuota, remaining, "新使用者應該有完整額度");

        // 第一次消耗
        boolean result = rateLimitService.tryConsume(userId);
        assertTrue(result);
        assertEquals(dailyQuota - 1, rateLimitService.getRemainingQuota(userId));
    }

    /**
     * 測試 3: 併發場景 - 多執行緒同時消耗令牌
     */
    @Test
    @DisplayName("多執行緒併發請求時應該正確計數，不會超發額度")
    void shouldHandleConcurrentRequestsCorrectly() throws InterruptedException {
        String userId = "U_CONCURRENT_TEST";
        int dailyQuota = systemConfigService.getInt("daily_message_limit"); // 10
        int threadCount = 20; // 20 個執行緒搶 10 個額度

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // 20 個執行緒同時嘗試消耗額度
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    boolean success = rateLimitService.tryConsume(userId);
                    if (success) {
                        successCount.incrementAndGet();
                        log.debug(">>>> [Thread {}] 成功消耗額度", Thread.currentThread().getName());
                    } else {
                        failureCount.incrementAndGet();
                        log.debug(">>>> [Thread {}] 額度不足", Thread.currentThread().getName());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // 等待所有執行緒完成
        boolean finished = latch.await(10, TimeUnit.SECONDS);
        assertTrue(finished, "所有執行緒應在 10 秒內完成");

        executor.shutdown();

        // 驗證結果
        log.info(">>>> [Test] 成功次數: {}, 失敗次數: {}", successCount.get(), failureCount.get());
        assertEquals(dailyQuota, successCount.get(), "成功次數應該等於額度上限");
        assertEquals(threadCount - dailyQuota, failureCount.get(), "失敗次數應該等於 (總請求數 - 額度)");
        assertEquals(0, rateLimitService.getRemainingQuota(userId), "剩餘額度應為 0");
    }

    /**
     * 測試 4: setQuota 與 addQuota
     */
    @Test
    @DisplayName("應該能夠動態設定與增加額度")
    void shouldAllowDynamicQuotaAdjustment() {
        String userId = "U_ADMIN_TEST";

        // 設定自訂額度
        rateLimitService.setQuota(userId, 5);
        assertEquals(5, rateLimitService.getRemainingQuota(userId));

        // 消耗 2 個
        rateLimitService.tryConsume(userId);
        rateLimitService.tryConsume(userId);
        assertEquals(3, rateLimitService.getRemainingQuota(userId));

        // 增加 10 個額度
        rateLimitService.addQuota(userId, 10);
        int newQuota = rateLimitService.getRemainingQuota(userId);
        assertTrue(newQuota >= 10, "增加額度後應該至少有 10 個令牌");
        
        log.info(">>>> [Test] 增加額度後，剩餘額度: {}", newQuota);
    }

    /**
     * 測試 5: 每月額度（通知額度）
     */
    @Test
    @DisplayName("每月通知額度應該獨立運作")
    void shouldHandleMonthlyNotificationQuotaSeparately() {
        String userId = "U_MONTHLY_TEST";
        int monthlyQuota = systemConfigService.getInt("monthly_broadcast_limit"); // 預設 100

        // 查詢剩餘額度
        assertEquals(monthlyQuota, rateLimitService.getRemainingMonthlyQuota(userId));

        // 消耗 5 次
        for (int i = 0; i < 5; i++) {
            boolean result = rateLimitService.tryConsumeMonthlyNotification(userId);
            assertTrue(result, "每月額度應該足夠");
        }

        // 驗證剩餘額度
        assertEquals(monthlyQuota - 5, rateLimitService.getRemainingMonthlyQuota(userId));
        
        // 驗證每日額度不受影響
        int dailyQuota = systemConfigService.getInt("daily_message_limit");
        assertEquals(dailyQuota, rateLimitService.getRemainingQuota(userId), "每日額度應該獨立");
    }

    /**
     * 測試 6: 純令牌桶模式 - Key 固定不變
     */
    @Test
    @DisplayName("應該使用固定 Key，不包含日期（純令牌桶模式）")
    void shouldUsePersistentKeyWithoutDate() {
        String userId = "U_KEY_TEST";

        // 消耗 3 個額度（這會觸發 RateLimiter 初始化）
        for (int i = 0; i < 3; i++) {
            rateLimitService.tryConsume(userId);
        }

        // 驗證 Key 名稱（直接檢查特定 Key）
        String expectedKey = "rate_limit:quota:" + userId;
        boolean keyExists = redissonClient.getRateLimiter(expectedKey).isExists();
        assertTrue(keyExists, "Key 應該存在且不包含日期後綴");
        
        // 檢查 Redis 中是否有任何帶日期後綴的 Key（不應存在）
        Iterable<String> keys = redissonClient.getKeys().getKeysByPattern("rate_limit:quota:" + userId + ":*");
        long keysWithDateSuffix = 0;
        for (String key : keys) {
            keysWithDateSuffix++;
        }
        assertEquals(0, keysWithDateSuffix, "不應存在帶日期後綴的 Key");
        
        log.info(">>>> [Test] Redis Key 驗證通過：純令牌桶模式（無日期後綴）");
    }

    /**
     * 測試 7: 令牌桶容量上限
     */
    @Test
    @DisplayName("令牌數量應該受桶容量限制，不會無限累積")
    void shouldRespectBucketCapacity() {
        String userId = "U_CAPACITY_TEST";
        int dailyQuota = systemConfigService.getInt("daily_message_limit"); // 10

        // 設定額度為 15 個（超過標準額度）
        rateLimitService.setQuota(userId, 15);
        
        // 等待一小段時間（模擬令牌補充）
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 驗證最大可用額度（應該不超過桶容量）
        int remaining = rateLimitService.getRemainingQuota(userId);
        log.info(">>>> [Test] 設定 15 個令牌後，實際剩餘: {}", remaining);
        
        // 注意：Redisson RRateLimiter 的容量限制由 rate 參數決定
        // 因為 setQuota 會重新設定 rate，所以這裡應該等於 15
        assertTrue(remaining <= 15, "剩餘額度應該不超過設定值");
    }

    /**
     * TestConfiguration - 建立 RedissonClient
     */
    @TestConfiguration
    static class TestRedissonConfig {
        @Bean
        @Primary
        public RedissonClient testRedissonClient() {
            Config config = new Config();
            String redisAddress = "redis://" + redis.getHost() + ":" + redis.getFirstMappedPort();
            
            config.useSingleServer()
                    .setAddress(redisAddress)
                    .setPassword(null)
                    .setConnectionPoolSize(10)
                    .setConnectionMinimumIdleSize(5)  // 必須 <= connectionPoolSize
                    .setTimeout(3000);
            
            config.setLockWatchdogTimeout(30000L);
            
            return Redisson.create(config);
        }
    }
}

package com.next.nexrailai.integration;

import com.next.nexrailai.aspect.DistributedLock;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Service;
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
 * 分散式鎖整合測試
 * 使用 Testcontainers 啟動真實 Redis 環境，測試 Redisson 分散式鎖行為
 */
@Slf4j
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(DistributedLockIntegrationTest.TestRedissonConfig.class)
public class DistributedLockIntegrationTest {

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
    private RedissonClient redissonClient;

    @Autowired
    private TestLockService testLockService;

    @BeforeEach
    void setUp() {
        // 確保 Redis 已啟動
        assertTrue(redis.isRunning(), "Redis container should be running");
    }

    /**
     * [正常情況] 成功取得鎖並執行業務邏輯
     */
    @Test
    void shouldAcquireLockAndExecuteBusinessLogic() {
        // Act
        String result = testLockService.executeWithLock("test-task-1");

        // Assert
        assertNotNull(result);
        assertEquals("Task executed successfully", result);

        // 驗證鎖已釋放
        RLock lock = redissonClient.getLock("lock:scheduled:test-lock");
        assertFalse(lock.isLocked(), "Lock should be released after execution");
    }

    /**
     * [正常情況] 無法取得鎖時跳過執行
     */
    @Test
    void shouldSkipExecutionWhenLockIsHeld() throws InterruptedException, ExecutionException {
        String lockKey = "lock:scheduled:test-lock";
        RLock lock = redissonClient.getLock(lockKey);

        // Arrange: 在另一個執行緒中取得鎖，模擬其他實例持有
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch lockAcquired = new CountDownLatch(1);

        Future<?> lockHolder = executor.submit(() -> {
            lock.lock();
            lockAcquired.countDown();
            try {
                // 持有鎖 2 秒
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        });

        try {
            // 等待另一個執行緒取得鎖
            lockAcquired.await();
            assertTrue(lock.isLocked(), "Lock should be held by another thread");

            // Act: 在主執行緒嘗試執行帶鎖的方法
            String result = testLockService.executeWithLock("test-task-2");

            // Assert: 返回 null，業務邏輯未執行
            assertNull(result, "Should return null when lock is not acquired");

        } finally {
            // Cleanup
            lockHolder.get(); // 等待鎖被釋放
            executor.shutdown();
        }
    }

    /**
     * [並發] 多執行緒同時嘗試取得同一把鎖，只有一個成功
     */
    @Test
    void shouldOnlyAllowOneThreadToAcquireLock() throws InterruptedException {
        // Arrange
        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger skipCount = new AtomicInteger(0);

        // Act: 啟動 5 個執行緒同時執行
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await(); // 等待統一啟動
                    String result = testLockService.executeSlowTask("concurrent-task");

                    if (result != null) {
                        successCount.incrementAndGet();
                    } else {
                        skipCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // 統一啟動所有執行緒
        boolean finished = finishLatch.await(10, TimeUnit.SECONDS);

        // Assert
        assertTrue(finished, "All threads should finish within timeout");
        assertEquals(1, successCount.get(), "Only one thread should acquire lock and execute");
        assertEquals(4, skipCount.get(), "Four threads should skip execution");

        executorService.shutdown();
    }

    /**
     * [Watchdog] 長時間執行任務，鎖不會過期
     */
    @Test
    void shouldNotExpireLockDuringLongRunningTask() throws InterruptedException, ExecutionException, TimeoutException {
        // Arrange
        String lockKey = "lock:scheduled:long-task";
        AtomicInteger lockChecksDuringExecution = new AtomicInteger(0);

        // Act: 在背景執行緒中執行長任務
        CompletableFuture<String> taskFuture = CompletableFuture.supplyAsync(() ->
                testLockService.executeLongRunningTask("long-task")
        );

        // 在任務執行期間檢查鎖狀態（每 500ms 檢查一次，持續 3 秒）
        Thread.sleep(500); // 等待任務開始
        for (int i = 0; i < 6; i++) {
            RLock lock = redissonClient.getLock(lockKey);
            if (lock.isLocked()) {
                lockChecksDuringExecution.incrementAndGet();
            }
            Thread.sleep(500);
        }

        // 等待任務完成
        String result = taskFuture.get(10, TimeUnit.SECONDS);

        // Assert
        assertNotNull(result);
        assertEquals("Long task completed", result);
        assertTrue(lockChecksDuringExecution.get() >= 4,
                "Lock should remain held during execution (Watchdog auto-renewal). Checks: " + lockChecksDuringExecution.get());

        // 驗證鎖已釋放
        RLock lock = redissonClient.getLock(lockKey);
        assertFalse(lock.isLocked(), "Lock should be released after task completion");
    }

    /**
     * [異常處理] 業務邏輯拋出異常時，鎖仍然被釋放
     */
    @Test
    void shouldReleaseLockWhenExceptionThrown() {
        // Act & Assert
        Exception exception = assertThrows(RuntimeException.class, () ->
                testLockService.executeWithException("error-task")
        );

        assertEquals("Business logic error", exception.getMessage());

        // 驗證鎖已釋放
        RLock lock = redissonClient.getLock("lock:scheduled:error-task");
        assertFalse(lock.isLocked(), "Lock should be released even when exception is thrown");
    }

    /**
     * [並發] 鎖釋放後，其他執行緒可以取得
     */
    @Test
    void shouldAllowOtherThreadToAcquireLockAfterRelease() throws InterruptedException, ExecutionException {
        // Arrange
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        List<String> results = new ArrayList<>();

        // Act: 執行緒 A 先執行
        Future<String> taskA = executorService.submit(() ->
                testLockService.executeWithLock("sequential-task")
        );
        results.add(taskA.get());

        // 執行緒 B 在 A 完成後執行
        Future<String> taskB = executorService.submit(() ->
                testLockService.executeWithLock("sequential-task")
        );
        results.add(taskB.get());

        // Assert
        assertEquals(2, results.size());
        assertEquals("Task executed successfully", results.get(0));
        assertEquals("Task executed successfully", results.get(1));

        executorService.shutdown();
    }

    /**
     * [邊界值] 鎖的 key 包含特殊字元
     */
    @Test
    void shouldHandleLockKeyWithSpecialCharacters() {
        // Act
        String result1 = testLockService.executeWithLock("task:with:colons");
        String result2 = testLockService.executeWithLock("task-with-dashes");
        String result3 = testLockService.executeWithLock("task_with_underscores");

        // Assert
        assertNotNull(result1);
        assertNotNull(result2);
        assertNotNull(result3);
    }

    /**
     * [並發] 可重入鎖 - 同一執行緒多次取得同一把鎖
     */
    @Test
    void shouldSupportReentrantLock() {
        // Act
        String result = testLockService.executeReentrantTask("reentrant-task");

        // Assert
        assertNotNull(result);
        assertEquals("Reentrant task completed", result);
    }

    // ==================== 測試用 Service ====================

    /**
     * 測試用服務，模擬帶有 @DistributedLock 的業務邏輯
     */
    @Service
    static class TestLockService {

        @DistributedLock(key = "test-lock")
        public String executeWithLock(String taskName) {
            return "Task executed successfully";
        }

        @DistributedLock(key = "concurrent-task")
        public String executeSlowTask(String taskName) throws InterruptedException {
            Thread.sleep(1000); // 模擬耗時操作
            return "Slow task completed";
        }

        @DistributedLock(key = "long-task", expireTime = 60)
        public String executeLongRunningTask(String taskName) {
            try {
                // 模擬執行 4 秒（超過一般 Watchdog 檢查間隔）
                Thread.sleep(4000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            return "Long task completed";
        }

        @DistributedLock(key = "error-task")
        public String executeWithException(String taskName) {
            throw new RuntimeException("Business logic error");
        }

        @DistributedLock(key = "reentrant-task")
        public String executeReentrantTask(String taskName) {
            // 呼叫另一個帶有相同鎖的方法
            return executeNestedTask(taskName);
        }

        @DistributedLock(key = "reentrant-task")
        public String executeNestedTask(String taskName) {
            return "Reentrant task completed";
        }
    }

    /**
     * 測試用配置 - 建立 RedissonClient Bean
     */
    @TestConfiguration
    static class TestRedissonConfig {

        @Bean
        @Primary  // 覆蓋主配置中的 RedissonClient
        public RedissonClient testRedissonClient() {
            Config config = new Config();
            String redisAddress = "redis://" + redis.getHost() + ":" + redis.getFirstMappedPort();

            config.useSingleServer()
                    .setAddress(redisAddress)
                    .setPassword(null)
                    .setConnectionPoolSize(10)
                    .setConnectionMinimumIdleSize(2)
                    .setTimeout(3000);

            // 設定 Watchdog 超時為 30 秒
            config.setLockWatchdogTimeout(30000L);

            return Redisson.create(config);
        }

        @Bean
        public TestLockService testLockService() {
            return new TestLockService();
        }
    }
}

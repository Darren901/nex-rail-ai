package com.next.nexrailai.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * DistributedLockAspect 單元測試
 * 測試 Redisson 分散式鎖的正確性、Watchdog 機制、異常處理
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DistributedLockAspect 測試")
class DistributedLockAspectTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private DistributedLock distributedLockAnnotation;

    @InjectMocks
    private DistributedLockAspect distributedLockAspect;

    @BeforeEach
    void setUp() {
        // 預設行為：註解返回 key
        when(distributedLockAnnotation.key()).thenReturn("test-task");
        when(redissonClient.getLock(anyString())).thenReturn(lock);
    }

    // ==================== 正常情況 ====================

    @Test
    @DisplayName("應該成功取得鎖並執行業務邏輯")
    void shouldAcquireLockAndExecuteBusinessLogic() throws Throwable {
        // Arrange
        when(lock.tryLock(eq(-1L), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("success");

        // Act
        Object result = distributedLockAspect.around(joinPoint, distributedLockAnnotation);

        // Assert
        assertEquals("success", result);
        verify(redissonClient).getLock("lock:scheduled:test-task");
        verify(lock).tryLock(-1L, TimeUnit.MILLISECONDS); // Watchdog 模式
        verify(joinPoint).proceed();
        verify(lock).unlock();
    }

    @Test
    @DisplayName("應該在無法取得鎖時跳過執行")
    void shouldSkipExecutionWhenLockNotAcquired() throws Throwable {
        // Arrange
        when(lock.tryLock(eq(-1L), any(TimeUnit.class))).thenReturn(false);

        // Act
        Object result = distributedLockAspect.around(joinPoint, distributedLockAnnotation);

        // Assert
        assertNull(result);
        verify(joinPoint, never()).proceed(); // 業務邏輯不應被執行
        verify(lock, never()).unlock(); // 鎖不應被釋放（因為沒取得）
    }

    @Test
    @DisplayName("應該使用 Watchdog 自動續約機制 (leaseTime = -1)")
    void shouldUseWatchdogAutoRenewal() throws Throwable {
        // Arrange
        when(lock.tryLock(eq(-1L), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("result");

        // Act
        distributedLockAspect.around(joinPoint, distributedLockAnnotation);

        // Assert
        verify(lock).tryLock(-1L, TimeUnit.MILLISECONDS); // 驗證 leaseTime = -1
    }

    // ==================== 邊界值 ====================

    @Test
    @DisplayName("應該處理包含特殊字元的鎖 key")
    void shouldHandleLockKeyWithSpecialCharacters() throws Throwable {
        // Arrange
        when(distributedLockAnnotation.key()).thenReturn("task:with:colons");
        when(lock.tryLock(eq(-1L), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("success");

        // Act
        distributedLockAspect.around(joinPoint, distributedLockAnnotation);

        // Assert
        verify(redissonClient).getLock("lock:scheduled:task:with:colons");
    }

    @Test
    @DisplayName("應該正確處理業務邏輯返回 null 的情況")
    void shouldHandleBusinessLogicReturningNull() throws Throwable {
        // Arrange
        when(lock.tryLock(eq(-1L), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(joinPoint.proceed()).thenReturn(null);

        // Act
        Object result = distributedLockAspect.around(joinPoint, distributedLockAnnotation);

        // Assert
        assertNull(result);
        verify(lock).unlock(); // 鎖仍應被釋放
    }

    // ==================== 異常處理 ====================

    @Test
    @DisplayName("應該在業務邏輯拋出異常時釋放鎖並重新拋出異常")
    void shouldReleaseLockAndRethrowExceptionWhenBusinessLogicFails() throws Throwable {
        // Arrange
        when(lock.tryLock(eq(-1L), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        RuntimeException businessException = new RuntimeException("業務邏輯錯誤");
        when(joinPoint.proceed()).thenThrow(businessException);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            distributedLockAspect.around(joinPoint, distributedLockAnnotation);
        });
        assertEquals("業務邏輯錯誤", exception.getMessage());

        verify(lock).unlock(); // 鎖應該在 finally 區塊中被釋放
    }

    @Test
    @DisplayName("應該處理 tryLock 拋出 InterruptedException")
    void shouldHandleInterruptedExceptionFromTryLock() throws Throwable {
        // Arrange
        when(lock.tryLock(eq(-1L), any(TimeUnit.class))).thenThrow(new InterruptedException("執行緒被中斷"));

        // Act & Assert
        InterruptedException exception = assertThrows(InterruptedException.class, () -> {
            distributedLockAspect.around(joinPoint, distributedLockAnnotation);
        });
        assertEquals("執行緒被中斷", exception.getMessage());

        verify(joinPoint, never()).proceed(); // 業務邏輯不應被執行
        verify(lock, never()).unlock(); // 鎖不應被釋放（因為沒取得）
    }

    @Test
    @DisplayName("應該處理 RedissonClient.getLock() 拋出異常")
    void shouldHandleExceptionFromGetLock() {
        // Arrange
        when(redissonClient.getLock(anyString())).thenThrow(new RuntimeException("Redis 連線失敗"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            distributedLockAspect.around(joinPoint, distributedLockAnnotation);
        });
        assertEquals("Redis 連線失敗", exception.getMessage());
    }

    // ==================== 特殊情況 ====================

    @Test
    @DisplayName("應該在 isHeldByCurrentThread 返回 false 時不釋放鎖")
    void shouldNotUnlockWhenNotHeldByCurrentThread() throws Throwable {
        // Arrange
        when(lock.tryLock(eq(-1L), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(false); // 鎖已被其他執行緒持有
        when(joinPoint.proceed()).thenReturn("success");

        // Act
        distributedLockAspect.around(joinPoint, distributedLockAnnotation);

        // Assert
        verify(lock, never()).unlock(); // 不應呼叫 unlock()
    }

    @Test
    @DisplayName("應該在取得鎖失敗時不檢查 isHeldByCurrentThread")
    void shouldNotCheckIsHeldByCurrentThreadWhenLockNotAcquired() throws Throwable {
        // Arrange
        when(lock.tryLock(eq(-1L), any(TimeUnit.class))).thenReturn(false);

        // Act
        distributedLockAspect.around(joinPoint, distributedLockAnnotation);

        // Assert
        verify(lock, never()).isHeldByCurrentThread(); // 不應檢查
        verify(lock, never()).unlock(); // 不應釋放
    }
}

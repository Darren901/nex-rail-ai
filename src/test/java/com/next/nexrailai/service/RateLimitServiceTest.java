package com.next.nexrailai.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateLimiterConfig;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private RedissonClient redissonClient;
    @Mock
    private SystemConfigService systemConfigService;
    @Mock
    private RRateLimiter rateLimiter;
    @Mock
    private RateLimiterConfig rateLimiterConfig;

    @InjectMocks
    private RateLimitService rateLimitService;

    @Test
    void tryConsume_ShouldReturnTrue_WhenQuotaAvailable() {
        // Arrange
        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        when(systemConfigService.getInt("daily_message_limit")).thenReturn(10);
        when(rateLimiter.isExists()).thenReturn(false);
        when(rateLimiter.tryAcquire(1)).thenReturn(true);
        when(rateLimiter.availablePermits()).thenReturn(9L);

        // Act
        boolean result = rateLimitService.tryConsume("U123");

        // Assert
        assertTrue(result);
        verify(rateLimiter).trySetRate(eq(RateType.OVERALL), eq(10L), eq(1L), eq(RateIntervalUnit.DAYS));
        verify(rateLimiter).tryAcquire(1);
    }

    @Test
    void tryConsume_ShouldReturnFalse_WhenQuotaExceeded() {
        // Arrange
        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        when(systemConfigService.getInt("daily_message_limit")).thenReturn(10);
        when(rateLimiter.isExists()).thenReturn(true); // 已初始化
        when(rateLimiter.tryAcquire(1)).thenReturn(false); // 無額度

        // Act
        boolean result = rateLimitService.tryConsume("U123");

        // Assert
        assertFalse(result);
        verify(rateLimiter, never()).trySetRate(any(), anyLong(), anyLong(), any()); // 不應該重新設定
        verify(rateLimiter).tryAcquire(1);
    }

    @Test
    void getRemainingQuota_ShouldReturnDefault_WhenRedisEmpty() {
        // Arrange
        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        when(rateLimiter.isExists()).thenReturn(false);
        when(systemConfigService.getInt("daily_message_limit")).thenReturn(10);

        // Act
        int result = rateLimitService.getRemainingQuota("U123");

        // Assert
        assertEquals(10, result);
    }

    @Test
    void getRemainingQuota_ShouldReturnActual_WhenRedisExists() {
        // Arrange
        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        when(rateLimiter.isExists()).thenReturn(true);
        when(rateLimiter.availablePermits()).thenReturn(7L);

        // Act
        int result = rateLimitService.getRemainingQuota("U123");

        // Assert
        assertEquals(7, result);
    }

    @Test
    void tryConsumeMonthlyNotification_ShouldReturnTrue_WhenQuotaAvailable() {
        // Arrange
        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        when(systemConfigService.getInt("monthly_broadcast_limit")).thenReturn(100);
        when(rateLimiter.isExists()).thenReturn(false);
        when(rateLimiter.tryAcquire(1)).thenReturn(true);
        when(rateLimiter.availablePermits()).thenReturn(99L);

        // Act
        boolean result = rateLimitService.tryConsumeMonthlyNotification("U123");

        // Assert
        assertTrue(result);
        verify(rateLimiter).trySetRate(eq(RateType.OVERALL), eq(100L), eq(32L), eq(RateIntervalUnit.DAYS));
    }

    @Test
    void setQuota_ShouldDeleteAndRecreate() {
        // Arrange
        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);

        // Act
        rateLimitService.setQuota("U123", 20);

        // Assert
        verify(rateLimiter).delete();
        verify(rateLimiter).trySetRate(eq(RateType.OVERALL), eq(20L), eq(1L), eq(RateIntervalUnit.DAYS));
    }

    @Test
    void addQuota_ShouldUseConfiguredRate_WhenLimiterExists() {
        // Arrange
        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        when(rateLimiter.isExists()).thenReturn(true);
        when(rateLimiter.getConfig()).thenReturn(rateLimiterConfig);
        when(rateLimiterConfig.getRate()).thenReturn(10L); // 目前設定的容量是 10
        
        // Act
        rateLimitService.addQuota("U123", 5); // 增加 5，新容量應為 15

        // Assert
        verify(rateLimiter).delete();
        verify(rateLimiter).trySetRate(eq(RateType.OVERALL), eq(15L), eq(1L), eq(RateIntervalUnit.DAYS));
    }

    @Test
    void addQuota_ShouldUseDefaultRate_WhenLimiterNotExists() {
        // Arrange
        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        when(rateLimiter.isExists()).thenReturn(false);
        when(systemConfigService.getInt("daily_message_limit")).thenReturn(10);
        
        // Act
        rateLimitService.addQuota("U123", 5); // 預設 10 + 增加 5 = 15

        // Assert
        verify(rateLimiter).delete();
        verify(rateLimiter).trySetRate(eq(RateType.OVERALL), eq(15L), eq(1L), eq(RateIntervalUnit.DAYS));
    }

    @Test
    void addMonthlyQuota_ShouldUseConfiguredRate_WhenLimiterExists() {
        // Arrange
        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        when(rateLimiter.isExists()).thenReturn(true);
        when(rateLimiter.getConfig()).thenReturn(rateLimiterConfig);
        when(rateLimiterConfig.getRate()).thenReturn(100L); // 目前設定的容量是 100
        
        // Act
        rateLimitService.addMonthlyQuota("U123", 50); // 增加 50，新容量應為 150

        // Assert
        verify(rateLimiter).delete();
        verify(rateLimiter).trySetRate(eq(RateType.OVERALL), eq(150L), eq(32L), eq(RateIntervalUnit.DAYS));
    }
}

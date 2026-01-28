package com.next.nexrailai.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private SystemConfigService systemConfigService;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private RateLimitService rateLimitService;

    @Test
    void tryConsume_ShouldReturnTrue_WhenQuotaAvailable() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(systemConfigService.getInt("daily_message_limit")).thenReturn(10);
        when(valueOperations.decrement(anyString())).thenReturn(9L); // 10 -> 9

        // Act
        boolean result = rateLimitService.tryConsume("U123");

        // Assert
        assertTrue(result);
        verify(valueOperations).setIfAbsent(anyString(), eq("10"), any(Duration.class));
    }

    @Test
    void tryConsume_ShouldReturnFalse_WhenQuotaExceeded() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(systemConfigService.getInt("daily_message_limit")).thenReturn(10);
        when(valueOperations.decrement(anyString())).thenReturn(-1L); // 0 -> -1

        // Act
        boolean result = rateLimitService.tryConsume("U123");

        // Assert
        assertFalse(result);
        verify(valueOperations).increment(anyString()); // Rollback
    }

    @Test
    void getRemainingQuota_ShouldReturnDefault_WhenRedisEmpty() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(systemConfigService.getInt("daily_message_limit")).thenReturn(10);

        // Act
        int result = rateLimitService.getRemainingQuota("U123");

        // Assert
        assertEquals(10, result);
    }
}

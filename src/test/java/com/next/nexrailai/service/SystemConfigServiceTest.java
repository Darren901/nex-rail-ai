package com.next.nexrailai.service;

import com.next.nexrailai.jpa.entity.SystemConfig;
import com.next.nexrailai.jpa.repository.SystemConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemConfigServiceTest {

    @Mock
    private SystemConfigRepository repository;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private SystemConfigService configService;

    @Test
    void get_ShouldReturnCachedValue_WhenCacheHit() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("20");

        // Act
        String result = configService.get("key");

        // Assert
        assertEquals("20", result);
        verify(repository, never()).findById(anyString());
    }

    @Test
    void get_ShouldQueryDB_WhenCacheMiss() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        
        SystemConfig config = new SystemConfig();
        config.setConfigValue("15");
        when(repository.findById("key")).thenReturn(Optional.of(config));

        // Act
        String result = configService.get("key");

        // Assert
        assertEquals("15", result);
        verify(valueOperations).set(anyString(), eq("15"), anyLong(), any());
    }

    @Test
    void updateConfig_ShouldUpdateDBAndCache() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(repository.findById("key")).thenReturn(Optional.empty()); // New config

        // Act
        configService.updateConfig("key", "new_val", "desc");

        // Assert
        verify(repository).save(any(SystemConfig.class));
        verify(valueOperations).set(contains("key"), eq("new_val"), anyLong(), any());
    }
}

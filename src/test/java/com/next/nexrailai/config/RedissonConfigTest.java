package com.next.nexrailai.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RedissonConfig 配置測試
 * 驗證 Redisson 客戶端配置的正確性
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",
        "spring.data.redis.password="
})
@DisplayName("RedissonConfig 測試")
class RedissonConfigTest {

    @Autowired(required = false)
    private RedissonClient redissonClient;

    @Test
    @DisplayName("應該成功建立 RedissonClient Bean")
    void shouldCreateRedissonClientBean() {
        // Assert
        assertNotNull(redissonClient, "RedissonClient 應該被成功建立");
    }

    @Test
    @DisplayName("應該使用正確的配置建立 Redisson 客戶端")
    void shouldConfigureRedissonClientCorrectly() {
        // Assert
        assertNotNull(redissonClient);
        
        // 驗證客戶端可以正常使用（不連線到 Redis 也能建立實例）
        Config config = redissonClient.getConfig();
        assertNotNull(config);
    }
}

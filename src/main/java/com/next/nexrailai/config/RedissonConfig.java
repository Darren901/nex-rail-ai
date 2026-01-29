package com.next.nexrailai.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 配置
 * 提供分散式鎖與自動續約（Watchdog）功能
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    /**
     * Redisson 客戶端
     * 
     * Watchdog 機制說明：
     * - 預設每 10 秒自動續約一次（lockWatchdogTimeout / 3）
     * - 預設鎖過期時間：30 秒（lockWatchdogTimeout）
     * - 如果業務邏輯執行超過 30 秒，Watchdog 會自動延長鎖的過期時間
     * - 直到業務邏輯完成，才會釋放鎖
     */
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        
        String redisAddress = "redis://" + redisHost + ":" + redisPort;
        
        config.useSingleServer()
                .setAddress(redisAddress)
                .setPassword(redisPassword.isEmpty() ? null : redisPassword)
                .setConnectionPoolSize(64)
                .setConnectionMinimumIdleSize(10)
                // Watchdog 超時時間（鎖的預設過期時間）
                .setTimeout(3000)
                .setRetryAttempts(3)
                .setRetryInterval(1500);

        // 設定 Watchdog 超時（預設 30 秒，可根據需求調整）
        config.setLockWatchdogTimeout(30000L); // 30 秒

        return Redisson.create(config);
    }
}

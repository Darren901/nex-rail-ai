package com.next.nexrailai.service;

import com.next.nexrailai.jpa.entity.SystemConfig;
import com.next.nexrailai.jpa.repository.SystemConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemConfigService {

    private final SystemConfigRepository repository;
    private final StringRedisTemplate redisTemplate;
    
    private static final String REDIS_KEY_PREFIX = "system:config:";

    // 定義預設值
    private static final Map<String, String> DEFAULTS = Map.of(
            "daily_message_limit", "10",
            "monthly_broadcast_limit", "5",
            "ticket_monitor_interval_seconds", "60",
            "welcome_message", "🎉 歡迎加入 NexRail AI！\n\n為了防止濫用，您的帳號目前處於「待審核」狀態。\n\n管理員審核通過後，您就可以開始使用智慧火車時刻查詢服務了！"
    );

    @PostConstruct
    public void init() {
        log.info(">>>> [SystemConfig] 初始化系統參數...");
        
        // 確保資料庫有預設值
        DEFAULTS.forEach((key, value) -> {
            if (!repository.existsById(key)) {
                repository.save(SystemConfig.builder()
                        .configKey(key)
                        .configValue(value)
                        .description("系統預設參數")
                        .build());
            }
        });
    }

    /**
     * 從 Redis 取得參數 (字串)
     */
    public String get(String key) {
        String redisKey = REDIS_KEY_PREFIX + key;
        
        // 查 Redis
        String cachedValue = redisTemplate.opsForValue().get(redisKey);
        if (cachedValue != null) {
            return cachedValue;
        }

        // 查 DB
        String dbValue = repository.findById(key)
                .map(SystemConfig::getConfigValue)
                .orElse(DEFAULTS.getOrDefault(key, ""));

        // 回寫 Redis (設定 1 小時過期，避免死資料，或是設久一點也行)
        redisTemplate.opsForValue().set(redisKey, dbValue, 24, TimeUnit.HOURS);
        
        return dbValue;
    }

    /**
     * 從 Redis 取得參數 (整數)
     */
    public int getInt(String key) {
        try {
            return Integer.parseInt(get(key));
        } catch (NumberFormatException e) {
            log.warn("Config key {} is not a number, using default 0", key);
            return 0;
        }
    }

    /**
     * 取得所有設定 (給管理後台用)
     */
    public List<SystemConfig> getAllConfigs() {
        return repository.findAll();
    }

    /**
     * 更新設定
     */
    @Transactional
    public void updateConfig(String key, String value, String description) {
        SystemConfig config = repository.findById(key)
                .orElse(SystemConfig.builder().configKey(key).build());
        
        config.setConfigValue(value);
        if (description != null) {
            config.setDescription(description);
        }
        
        repository.save(config);
        
        // 更新 Redis (直接覆蓋)
        String redisKey = REDIS_KEY_PREFIX + key;
        redisTemplate.opsForValue().set(redisKey, value, 24, TimeUnit.HOURS);
        
        log.info(">>>> [SystemConfig] Updated {}: {}", key, value);
    }

    /**
     * 重新載入所有快取 (通常不需要手動呼叫，因為 updateConfig 會自動更新單筆)
     */
    public void refreshCache() {
        repository.findAll().forEach(config -> {
            String redisKey = REDIS_KEY_PREFIX + config.getConfigKey();
            redisTemplate.opsForValue().set(redisKey, config.getConfigValue(), 24, TimeUnit.HOURS);
        });
        log.info(">>>> [SystemConfig] Cache refreshed from DB.");
    }
}

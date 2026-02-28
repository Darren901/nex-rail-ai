package com.next.nexrailai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.next.nexrailai.config.StockProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockApiServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private StockApiService stockApiService;

    @BeforeEach
    void setUp() {
        StockProperties.AlphaVantage alphaVantage = new StockProperties.AlphaVantage("test-key", "https://www.alphavantage.co");
        StockProperties.Finnhub finnhub = new StockProperties.Finnhub("test-key", "https://finnhub.io/api/v1");
        StockProperties props = new StockProperties("U001", alphaVantage, finnhub);
        stockApiService = new StockApiService(props, redisTemplate, new ObjectMapper());
    }

    @Test
    void formatPriceChange_shouldReturnPositiveString_whenPriceIncreased() {
        String result = stockApiService.formatPriceChange(100.0, 105.0);
        assertThat(result).startsWith("+");
        assertThat(result).contains("5.00%");
    }

    @Test
    void formatPriceChange_shouldReturnNegativeString_whenPriceDecreased() {
        String result = stockApiService.formatPriceChange(100.0, 95.0);
        assertThat(result).startsWith("-");
        assertThat(result).contains("5.00%");
    }

    @Test
    void formatPriceChange_shouldReturnZeroString_whenPriceUnchanged() {
        String result = stockApiService.formatPriceChange(100.0, 100.0);
        assertThat(result).startsWith("+");
        assertThat(result).contains("0.00%");
    }

    @Test
    void getCompanyNews_shouldReturnNonNullString_andNotThrow() {
        // Cache miss → 呼叫實際 API（test key 會失敗）→ 方法應回傳預設錯誤訊息，不拋例外
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        String result = stockApiService.getCompanyNews("AAPL");
        assertThat(result).isNotNull();
        assertThat(result).isNotEmpty();
    }
}

package com.next.nexrailai.service;

import com.next.nexrailai.config.StockProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class StockApiServiceTest {

    private StockApiService stockApiService;

    @BeforeEach
    void setUp() {
        StockProperties.AlphaVantage alphaVantage = new StockProperties.AlphaVantage("test-key", "https://www.alphavantage.co");
        StockProperties.Finnhub finnhub = new StockProperties.Finnhub("test-key", "https://finnhub.io/api/v1");
        StockProperties props = new StockProperties("U001", alphaVantage, finnhub);
        stockApiService = new StockApiService(props);
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
        // 呼叫會因無效 API key 而失敗，但方法本身不應拋出例外（防禦性設計）
        String result = stockApiService.getCompanyNews("AAPL");
        assertThat(result).isNotNull();
        assertThat(result).isNotEmpty();
    }
}

package com.next.nexrailai.service;

import com.next.nexrailai.jpa.entity.StockPosition;
import com.next.nexrailai.jpa.service.StockPositionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockReportServiceTest {

    @Mock
    private StockPositionService stockPositionService;

    @Mock
    private StockApiService stockApiService;

    @InjectMocks
    private StockReportService stockReportService;

    @Test
    void buildDailyReport_shouldContainAllSections_whenDataAvailable() {
        // given
        StockPosition aapl = StockPosition.builder()
            .symbol("AAPL")
            .shares(new BigDecimal("10"))
            .costPrice(new BigDecimal("180.00"))
            .build();
        when(stockPositionService.findAll()).thenReturn(List.of(aapl));
        when(stockApiService.getStockPrice("AAPL")).thenReturn(185.0);
        when(stockApiService.getMarketNews()).thenReturn(List.of("• Fed holds rates"));
        when(stockApiService.getFearAndGreedIndex()).thenReturn("😊 貪婪 (65)");

        // when
        String report = stockReportService.buildDailyReport();

        // then
        assertThat(report).contains("📊 美股日報");
        assertThat(report).contains("市場情緒");
        assertThat(report).contains("今日重點新聞");
        assertThat(report).contains("我的持倉");
        assertThat(report).contains("AAPL");
        assertThat(report).contains("+2.78%");
    }

    @Test
    void buildDailyReport_shouldHandleEmptyPositions_gracefully() {
        // given
        when(stockPositionService.findAll()).thenReturn(List.of());
        when(stockApiService.getMarketNews()).thenReturn(List.of("• No news"));
        when(stockApiService.getFearAndGreedIndex()).thenReturn("N/A");

        // when
        String report = stockReportService.buildDailyReport();

        // then
        assertThat(report).contains("📊 美股日報");
        assertThat(report).contains("尚未設定持倉");
    }

    @Test
    void buildDailyReport_shouldHandleApiFailure_gracefully() {
        // given
        StockPosition nvda = StockPosition.builder()
            .symbol("NVDA")
            .shares(new BigDecimal("5"))
            .costPrice(new BigDecimal("500.00"))
            .build();
        when(stockPositionService.findAll()).thenReturn(List.of(nvda));
        when(stockApiService.getStockPrice("NVDA")).thenReturn(-1.0);  // API 失敗
        when(stockApiService.getMarketNews()).thenReturn(List.of("• Market update"));
        when(stockApiService.getFearAndGreedIndex()).thenReturn("N/A");

        // when
        String report = stockReportService.buildDailyReport();

        // then
        assertThat(report).contains("NVDA");
        assertThat(report).contains("無法取得報價");
    }
}

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
class StockToolsTest {

    @Mock
    private StockApiService stockApiService;

    @Mock
    private StockPositionService stockPositionService;

    @InjectMocks
    private StockTools stockTools;

    @Test
    void getPortfolioPositions_shouldReturnFormattedPositions_whenPositionsExist() {
        StockPosition aapl = StockPosition.builder()
            .symbol("AAPL")
            .shares(new BigDecimal("10"))
            .costPrice(new BigDecimal("180.00"))
            .note("長線持有")
            .build();
        when(stockPositionService.findAll()).thenReturn(List.of(aapl));

        String result = stockTools.getPortfolioPositions();

        assertThat(result).contains("AAPL");
        assertThat(result).contains("10");
        assertThat(result).contains("180.00");
        assertThat(result).contains("長線持有");
    }

    @Test
    void getPortfolioPositions_shouldReturnEmptyMessage_whenNoPositions() {
        when(stockPositionService.findAll()).thenReturn(List.of());

        String result = stockTools.getPortfolioPositions();

        assertThat(result).contains("尚未設定持倉");
    }

    @Test
    void getStockPrice_shouldDelegateToApiService() {
        when(stockApiService.getStockPrice("AAPL")).thenReturn(185.5);

        double result = stockTools.getStockPrice("AAPL");

        assertThat(result).isEqualTo(185.5);
    }

    @Test
    void getMarketNews_shouldReturnJoinedString() {
        when(stockApiService.getMarketNews()).thenReturn(List.of("• News A", "• News B"));

        String result = stockTools.getMarketNews();

        assertThat(result).contains("News A");
        assertThat(result).contains("News B");
    }

    @Test
    void getFearAndGreedIndex_shouldDelegateToApiService() {
        when(stockApiService.getFearAndGreedIndex()).thenReturn("😨 恐慌 (32)");

        String result = stockTools.getFearAndGreedIndex();

        assertThat(result).isEqualTo("😨 恐慌 (32)");
    }
}

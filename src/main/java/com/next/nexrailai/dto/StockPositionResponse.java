package com.next.nexrailai.dto;

import com.next.nexrailai.jpa.entity.StockPosition;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StockPositionResponse(
    Long id,
    String symbol,
    BigDecimal shares,
    BigDecimal costPrice,
    String note,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static StockPositionResponse from(StockPosition entity) {
        return new StockPositionResponse(
            entity.getId(),
            entity.getSymbol(),
            entity.getShares(),
            entity.getCostPrice(),
            entity.getNote(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}

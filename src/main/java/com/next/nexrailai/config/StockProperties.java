package com.next.nexrailai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.stock")
public record StockProperties(
        String ownerLineUserId,
        AlphaVantage alphaVantage,
        Finnhub finnhub
) {
    public record AlphaVantage(String apiKey, String baseUrl) {}
    public record Finnhub(String apiKey, String baseUrl) {}
}

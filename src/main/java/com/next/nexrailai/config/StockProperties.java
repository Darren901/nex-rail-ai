package com.next.nexrailai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Getter;
import lombok.Setter;

@Component
@ConfigurationProperties(prefix = "app.stock")
@Getter
@Setter
public class StockProperties {

    private String ownerLineUserId;

    private AlphaVantage alphaVantage = new AlphaVantage();
    private Finnhub finnhub = new Finnhub();

    @Getter @Setter
    public static class AlphaVantage {
        private String apiKey;
        private String baseUrl;
    }

    @Getter @Setter
    public static class Finnhub {
        private String apiKey;
        private String baseUrl;
    }
}

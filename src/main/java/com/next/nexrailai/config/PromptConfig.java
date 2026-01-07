package com.next.nexrailai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConfigurationProperties(prefix = "ai.prompts")
@Getter
@Setter
public class PromptConfig {

    private Map<String, String> system;
    private Map<String, ToolConfig> tools;

    @Getter
    @Setter
    public static class ToolConfig{
        private String text;
    }
}

package com.next.nexrailai.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * 提取使用者記憶請求
 */
public record RecallMemoryRequest(
        @JsonProperty(required = true)
        @JsonPropertyDescription("要提取記憶的關鍵字標籤。")
        String key
) {}

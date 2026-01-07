package com.next.nexrailai.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * 儲存使用者記憶請求
 */
public record SaveMemoryRequest(
        @JsonProperty(required = true)
        @JsonPropertyDescription("記憶的關鍵字標籤，例如：'兒子'、'回家'、'出差'。")
        String key,
        @JsonProperty(required = true)
        @JsonPropertyDescription("要記憶的具體內容 JSON 字串。請包含 from, to, fareClass, cabinClass 等資訊。")
        String content
) {}

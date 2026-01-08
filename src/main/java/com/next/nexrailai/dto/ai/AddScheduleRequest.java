package com.next.nexrailai.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record AddScheduleRequest(
    @JsonProperty(required = true)
    @JsonPropertyDescription("提醒時間，格式必須為 YYYY-MM-DD HH:mm:ss。請根據使用者描述（例如『明天早上九點』）計算出準確時間。")
    String triggerTime,
    
    @JsonProperty(required = true)
    @JsonPropertyDescription("提醒內容。")
    String content
) {}

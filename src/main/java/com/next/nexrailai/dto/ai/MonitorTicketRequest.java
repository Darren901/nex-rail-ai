package com.next.nexrailai.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record MonitorTicketRequest(
    @JsonProperty(required = true)
    @JsonPropertyDescription("開始監控的時間，格式為 YYYY-MM-DD HH:mm:ss。")
    String startTime,

    @JsonProperty(required = true)
    @JsonPropertyDescription("起點車站名稱。")
    String from,

    @JsonProperty(required = true)
    @JsonPropertyDescription("終點車站名稱。")
    String to,

    @JsonProperty(required = true)
    @JsonPropertyDescription("乘車日期 (YYYY-MM-DD)。")
    String date,
    
    @JsonPropertyDescription("出發時間之後 (HH:mm)。")
    String time
) {}

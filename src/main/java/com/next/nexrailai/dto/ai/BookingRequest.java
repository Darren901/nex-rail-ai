package com.next.nexrailai.dto.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * 高鐵訂票請求
 */
public record BookingRequest(
        @JsonPropertyDescription("起點車站名稱。請務必回顧對話歷史，找出使用者最早查詢的出發站 (from)。")
        String from,

        @JsonPropertyDescription("終點車站名稱。請務必回顧對話歷史，找出使用者最早查詢的抵達站 (to)。")
        String to,

        @JsonPropertyDescription("乘車日期 (YYYY-MM-DD)。請從對話歷史中提取查詢日期。")
        String trainDate,

        @JsonPropertyDescription("該車次的原始出發時間 (HH:mm)。請從對話歷史的班次列表中查找該車次的時間。")
        String trainTime,

        @JsonPropertyDescription("要訂購的車次號碼，例如 '0837' 或 '1321'。")
        String trainNumber
) {}

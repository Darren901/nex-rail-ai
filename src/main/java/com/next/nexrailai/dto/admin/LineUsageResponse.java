package com.next.nexrailai.dto.admin;

public record LineUsageResponse(
        String type, // none (無限), limited (有限)
        long totalQuota, // 總額度 (如果是 none，可能為 0)
        long totalUsage,  // 已使用
        long todayReply,
        String statsDate // 統計數據的日期 (yyyy-MM-dd)
) {}

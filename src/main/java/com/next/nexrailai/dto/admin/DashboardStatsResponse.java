package com.next.nexrailai.dto.admin;

public record DashboardStatsResponse(
        long totalUsers,
        long activeTasks,
        long todayMessages, // 暫時保留，之後可從 Redis 或 Log 統計
        String systemUptime
) {}

package com.next.nexrailai.dto.admin;

public record DashboardStatsResponse(
        long totalUsers,
        long activeTasks,
        String systemUptime
) {}

package com.next.nexrailai.dto.admin;

import com.next.nexrailai.jpa.entity.AppUser;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record UserDetailResponse(
        String lineUserId,
        String displayName,
        String pictureUrl,
        AppUser.UserStatus status,
        LocalDateTime joinedAt,
        LocalDateTime lastActiveAt,
        int dailyQuota,
        int monthlyQuota
) {}

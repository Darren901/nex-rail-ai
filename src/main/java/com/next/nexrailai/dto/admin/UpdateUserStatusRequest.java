package com.next.nexrailai.dto.admin;

import com.next.nexrailai.jpa.entity.AppUser;

public record UpdateUserStatusRequest(
        AppUser.UserStatus status
) {}

package com.next.nexrailai.dto.admin;

public record ChangePasswordRequest(
        String oldPassword,
        String newPassword
) {}

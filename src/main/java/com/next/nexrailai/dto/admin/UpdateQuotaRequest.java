package com.next.nexrailai.dto.admin;

public record UpdateQuotaRequest(
        QuotaType type,
        QuotaAction action,
        int amount
) {
    public enum QuotaType {
        DAILY,
        MONTHLY
    }
    
    public enum QuotaAction {
        SET, // 直接設定為該數量
        ADD  // 增加 (或減少，若為負數)
    }
}

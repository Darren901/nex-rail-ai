package com.next.nexrailai.common;


import lombok.AllArgsConstructor;
import lombok.Getter;

public class Constant {

    @Getter
    @AllArgsConstructor
    public enum RCODE{
        SUCCESS					("0000", "回應成功"),
        TDX_TOKEN_ERROR         ("E001", "無法取得 TDX Token"),
        FROM_STATION_NOT_FOUND  ("E002", "找不到起點站"),
        TO_STATION_NOT_FOUND    ("E003", "找不到終點站"),
        MONTHLY_QUOTA_EXCEEDED  ("E004", "建立失敗：您本月的提醒/監控額度已達上限 (5/5)。請下個月再試。"),
        TASK_EXECUTOR_NOT_FOUND ("E005", "系統錯誤：找不到對應的任務執行器"),
        SYSTEM_ERROR            ("E999", "系統發生未預期的錯誤")
        ;


        private final String code;
        private final String message;

        /**
         * Builds a combined message containing the enum code and its message.
         *
         * @return the combined string in the format "code - message"
         */
        public String getFullMessage() {
            return code + " - " + message;
        }

    }

    @Getter
    @AllArgsConstructor
    public enum TicketType implements BaseEnum {
        ONE_WAY("單程票", 1),
        ROUND_TRIP("來回票", 2),
        EARLY_BIRD("早鳥票", 7),
        GROUP("團體票", 8);

        private final String name;
        private final int code;
    }

    @Getter
    @AllArgsConstructor
    public enum FareClass implements BaseEnum {
        ADULT("成人", 1),
        STUDENT("學生", 2),
        CHILD("孩童", 3),
        SENIOR("敬老", 4),
        DISABLED("愛心", 5),
        MILITARY("軍警", 8),
        CONCESSION("法優", 9);

        private final String name;
        private final int code;
    }

    @Getter
    @AllArgsConstructor
    public enum CabinClass implements BaseEnum {
        STANDARD("標準座", 1),
        BUSINESS("商務座", 2),
        NON_RESERVED("自由座", 3);

        private final String name;
        private final int code;
    }
}
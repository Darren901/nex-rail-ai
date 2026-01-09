package com.next.nexrailai.common;


import lombok.Getter;

public class Constant {

    @Getter
    public enum RCODE{
        SUCCESS					("0000", "回應成功"),
        TDX_TOKEN_ERROR         ("E001", "無法取得 TDX Token"),
        FROM_STATION_NOT_FOUND  ("E002", "找不到起點站"),
        TO_STATION_NOT_FOUND    ("E003", "找不到終點站"),
        MONTHLY_QUOTA_EXCEEDED  ("E004", "建立失敗：您本月的提醒/監控額度已達上限 (5/5)。請下個月再試。")
        ;


        private final String code;
        private final String message;

        RCODE(String code, String message){
            this.code = code;
            this.message = message;
        }

        public String getFullMessage() {
            return code + " - " + message;
        }

    }

    @Getter
    public enum TicketType {
        ONE_WAY("單程票", 1),
        ROUND_TRIP("來回票", 2),
        EARLY_BIRD("早鳥票", 7),
        GROUP("團體票", 8);

        private final String name;
        private final int code;

        TicketType(String name, int code) {
            this.name = name;
            this.code = code;
        }

        public static TicketType fromName(String name) {
            for (TicketType type : values()) {
                if (type.name.equals(name)) {
                    return type;
                }
            }
            return null;
        }
        
        public static TicketType fromCode(int code) {
            for (TicketType type : values()) {
                if (type.code == code) {
                    return type;
                }
            }
            return null;
        }
    }

    @Getter
    public enum FareClass {
        ADULT("成人", 1),
        STUDENT("學生", 2),
        CHILD("孩童", 3),
        SENIOR("敬老", 4),
        DISABLED("愛心", 5),
        MILITARY("軍警", 8),
        CONCESSION("法優", 9);

        private final String name;
        private final int code;

        FareClass(String name, int code) {
            this.name = name;
            this.code = code;
        }

        public static FareClass fromName(String name) {
            for (FareClass fc : values()) {
                if (fc.name.equals(name)) {
                    return fc;
                }
            }
            return null;
        }
        
        public static FareClass fromCode(int code) {
            for (FareClass fc : values()) {
                if (fc.code == code) {
                    return fc;
                }
            }
            return null;
        }
    }

    @Getter
    public enum CabinClass {
        STANDARD("標準座", 1),
        BUSINESS("商務座", 2),
        NON_RESERVED("自由座", 3);

        private final String name;
        private final int code;

        CabinClass(String name, int code) {
            this.name = name;
            this.code = code;
        }

        public static CabinClass fromName(String name) {
            for (CabinClass cc : values()) {
                if (cc.name.equals(name)) {
                    return cc;
                }
            }
            return null;
        }

        public static CabinClass fromCode(int code) {
            for (CabinClass cc : values()) {
                if (cc.code == code) {
                    return cc;
                }
            }
            return null;
        }
    }
}

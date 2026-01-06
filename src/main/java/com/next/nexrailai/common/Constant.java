package com.next.nexrailai.common;


import lombok.Getter;

public class Constant {

    @Getter
    public enum RCODE{
        SUCCESS					("0000", "回應成功"),
        TDX_TOKEN_ERROR         ("E001", "無法取得 TDX Token"),
        FROM_STATION_NOT_FOUND  ("E002", "找不到起點站"),
        TO_STATION_NOT_FOUND    ("E003", "找不到終點站"),
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
    public enum USER_STATUS{
        ENABLE  ("1", "啟用"),
        DISABLE ("0", "停用")
        ;

        private final String code;
        private final String message;

        USER_STATUS(String code, String message) {
            this.code = code;
            this.message = message;
        }
    }
}

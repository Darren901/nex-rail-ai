package com.next.nexrailai.common;


public class ApBusinessException extends RuntimeException {

    private String errorCode;
    private String errorMessage;

    public ApBusinessException(Constant.RCODE rcode) {
        super(rcode.getFullMessage());
    }

    public String getFullMessage() {
        return String.format("%s - %s", errorCode, errorMessage);
    }
}

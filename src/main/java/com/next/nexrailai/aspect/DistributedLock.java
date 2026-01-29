package com.next.nexrailai.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 分散式鎖註解
 * 用於排程任務，確保多實例環境下只有一個實例執行
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {
    
    /**
     * 鎖的 key（會自動加上 "lock:scheduled:" 前綴）
     */
    String key();
    
    /**
     * 鎖的過期時間（秒）
     * 預設 300 秒 (5 分鐘)
     */
    long expireTime() default 300;
}

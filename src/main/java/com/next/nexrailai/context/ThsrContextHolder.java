package com.next.nexrailai.context;

import com.next.nexrailai.dto.ThsrSummaryDTO;
import lombok.Builder;
import lombok.Data;

import java.util.List;

public class ThsrContextHolder {

    private static final ThreadLocal<ThsrSearchResult> context = new ThreadLocal<>();

    public static void set(ThsrSearchResult result) {
        context.set(result);
    }

    public static ThsrSearchResult get() {
        return context.get();
    }

    public static void clear() {
        context.remove();
    }

    @Data
    @Builder
    public static class ThsrSearchResult {
        private List<ThsrSummaryDTO> trains;
        private String origin;
        private String destination;
        
        // 訂票專用欄位
        private String bookingLink;
        private String trainDate;
        private String trainTime;
        private String trainNumber;
    }
}

package com.next.nexrailai.service;

import com.next.nexrailai.dto.ThsrTimetableDTO;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

@Component
public class ThsrTicketTools {

    public ToolCallback thsrTicketSearch(ThsrTicketService service) {
        return FunctionToolCallback
                .builder("thsrTicketSearch", (Function<ThsrTicketService.SearchRequest, List<ThsrTimetableDTO>>)
                        request -> service.searchTickets(
                                request.from(),
                                request.to(),
                                request.date(),
                                request.time()
                        ))
                .description("查詢台灣高鐵時刻表。參數包含起點站、終點站、日期(yyyy-MM-dd)及出發時間(HH:mm)")
                .inputType(ThsrTicketService.SearchRequest.class) // 使用你定義好的 Record
                .build();
    }
}

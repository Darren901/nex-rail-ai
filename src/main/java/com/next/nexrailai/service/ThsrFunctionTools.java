package com.next.nexrailai.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.next.nexrailai.config.PromptConfig;
import com.next.nexrailai.dto.ThsrSummaryDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class ThsrFunctionTools {

    private final PromptConfig promptConfig;
    private final ThsrTicketService service;

    public ToolCallback bookTicket() {
        return FunctionToolCallback
                .builder("bookTicket", (Function<ThsrTicketService.BookingRequest, String>)
                        service::bookTicket)
                .description(promptConfig.getBookTicket().get("text"))
                .inputType(ThsrTicketService.BookingRequest.class)
                .build();
    }

    public ToolCallback thsrJourneySearch() {
        return FunctionToolCallback
                .builder("thsrJourneySearch", (Function<ThsrTicketService.SearchRequest, List<ThsrSummaryDTO>>)
                        service::searchTickets)
                .description(promptConfig.getJourneySearch().get("text"))
                .inputType(ThsrTicketService.SearchRequest.class)
                .build();
    }
}

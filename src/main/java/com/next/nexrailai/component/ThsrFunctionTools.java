package com.next.nexrailai.component;

import com.next.nexrailai.config.PromptConfig;
import com.next.nexrailai.context.ThsrContextHolder;
import com.next.nexrailai.dto.ThsrSummaryDTO;
import com.next.nexrailai.dto.ai.BookingRequest;
import com.next.nexrailai.dto.ai.RecallMemoryRequest;
import com.next.nexrailai.dto.ai.SaveMemoryRequest;
import com.next.nexrailai.dto.ai.SearchRequest;
import com.next.nexrailai.service.ThsrTicketService;
import com.next.nexrailai.jpa.service.UserMemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ThsrFunctionTools {

    private final PromptConfig promptConfig;
    private final ThsrTicketService service;
    private final UserMemoryService memoryService;


    public ToolCallback saveUserMemory(String chatId) {
        return FunctionToolCallback
                .builder("saveUserMemory", (SaveMemoryRequest req) -> 
                        memoryService.saveMemory(chatId, req.key(), req.content()))
                .description(promptConfig.getTools().get("save-user-memory").getText())
                .inputType(SaveMemoryRequest.class)
                .build();
    }

    public ToolCallback recallUserMemory(String chatId) {
        return FunctionToolCallback
                .builder("recallUserMemory", (RecallMemoryRequest req) -> 
                        memoryService.recallMemory(chatId, req.key()))
                .description(promptConfig.getTools().get("recall-user-memory").getText())
                .inputType(RecallMemoryRequest.class)
                .build();
    }

    public ToolCallback bookTicket() {
        return FunctionToolCallback
                .builder("bookTicket", (BookingRequest req) -> {
                    String link = service.bookTicket(req);
                    
                    // 如果成功取得連結 (且不是錯誤提示訊息)，寫入 Context
                    if (link != null && link.startsWith("http")) {
                        ThsrContextHolder.set(ThsrContextHolder.ThsrSearchResult.builder()
                                .bookingLink(link)
                                .origin(req.from())
                                .destination(req.to())
                                .trainDate(req.trainDate())
                                .trainTime(req.trainTime())
                                .trainNumber(req.trainNumber())
                                .build());
                    }
                    return link;
                })
                .description(promptConfig.getTools().get("book-ticket").getText())
                .inputType(BookingRequest.class)
                .build();
    }

    public ToolCallback thsrJourneySearch() {
        return FunctionToolCallback
                .builder("thsrJourneySearch", (SearchRequest req) -> {
                    List<ThsrSummaryDTO> results = service.searchTickets(req);
                    
                    // 寫入 Context 以便後續產生 Flex Message
                    ThsrContextHolder.set(ThsrContextHolder.ThsrSearchResult.builder()
                            .trains(results)
                            .origin(req.from())
                            .destination(req.to())
                            .build());
                            
                    return results;
                })
                .description(promptConfig.getTools().get("journey-search").getText())
                .inputType(SearchRequest.class)
                .build();
    }
}

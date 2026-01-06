package com.next.nexrailai.component;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.next.nexrailai.config.PromptConfig;
import com.next.nexrailai.dto.ThsrSummaryDTO;
import com.next.nexrailai.service.ThsrTicketService;
import com.next.nexrailai.service.UserMemoryService;
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
    private final UserMemoryService memoryService;

    // --- 記憶功能 Request 定義 ---

    public record SaveMemoryRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("記憶的關鍵字標籤，例如：'兒子'、'回家'、'出差'。")
            String key,
            @JsonProperty(required = true)
            @JsonPropertyDescription("要記憶的具體內容 JSON 字串。請包含 from, to, fareClass, cabinClass 等資訊。")
            String content
    ) {}

    public record RecallMemoryRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("要提取記憶的關鍵字標籤。")
            String key
    ) {}

    // --- 工具 Callback 註冊 ---

    public ToolCallback saveUserMemory(String chatId) {
        return FunctionToolCallback
                .builder("saveUserMemory", (SaveMemoryRequest req) -> 
                        memoryService.saveMemory(chatId, req.key(), req.content()))
                .description("儲存使用者的常用行程或偏好設定。")
                .inputType(SaveMemoryRequest.class)
                .build();
    }

    public ToolCallback recallUserMemory(String chatId) {
        return FunctionToolCallback
                .builder("recallUserMemory", (RecallMemoryRequest req) -> 
                        memoryService.recallMemory(chatId, req.key()))
                .description("提取使用者先前儲存的常用行程或偏好設定。")
                .inputType(RecallMemoryRequest.class)
                .build();
    }

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

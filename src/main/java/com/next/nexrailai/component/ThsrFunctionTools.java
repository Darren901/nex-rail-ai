package com.next.nexrailai.component;

import com.next.nexrailai.common.ApBusinessException;
import com.next.nexrailai.config.PromptConfig;
import com.next.nexrailai.context.ThsrContextHolder;
import com.next.nexrailai.dto.ThsrSummaryDTO;
import com.next.nexrailai.dto.ai.*;
import com.next.nexrailai.service.ScheduleService;
import com.next.nexrailai.service.ThsrTicketService;
import com.next.nexrailai.jpa.service.UserMemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ThsrFunctionTools {

    private final PromptConfig promptConfig;
    private final ThsrTicketService service;
    private final UserMemoryService memoryService;
    private final ScheduleService scheduleService;

    public ToolCallback addSchedule(String chatId) {
        return FunctionToolCallback
                .builder("addSchedule", (AddScheduleRequest req) -> {
                    try {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                        LocalDateTime triggerTime = LocalDateTime.parse(req.triggerTime(), formatter);
                        
                        scheduleService.createReminder(chatId, triggerTime, req.content());
                        
                        return "已成功設定提醒！將在 " + req.triggerTime() + " 提醒您：" + req.content();
                    } catch (ApBusinessException e){
                        return e.getMessage();
                    } catch (Exception e) {
                        return "設定提醒失敗，時間格式錯誤。請確保格式為 YYYY-MM-DD HH:mm:ss";
                    }
                })
                .description(promptConfig.getTools().get("add-schedule").getText())
                .inputType(AddScheduleRequest.class)
                .build();
    }
    
    public ToolCallback monitorTicket(String chatId) {
        return FunctionToolCallback
                .builder("monitorTicket", (MonitorTicketRequest req) -> {
                    try {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                        LocalDateTime startTime = LocalDateTime.parse(req.startTime(), formatter);
                        
                        // 轉換為 SearchRequest
                        SearchRequest searchRequest = new SearchRequest(
                                req.from(), req.to(), req.date(), req.time(), null, null, null
                        );
                        
                        scheduleService.createTicketMonitor(chatId, startTime, searchRequest);
                        
                        return "已設定搶票監控任務！\n起始時間: " + req.startTime() + "\n監控班次: " + req.date() + " " + (req.time() != null ? req.time() : "") + " " + req.from() + " -> " + req.to() + "\n如果發現有位子，我會立刻通知您！";
                    } catch (ApBusinessException e){
                        return e.getMessage();
                    } catch (Exception e) {
                        return "設定監控失敗，時間格式錯誤。";
                    }
                })
                .description(promptConfig.getTools().get("monitor-ticket").getText())
                .inputType(MonitorTicketRequest.class)
                .build();
    }

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
                            .trainDate(req.date())
                            .build());
                            
                    return results;
                })
                .description(promptConfig.getTools().get("journey-search").getText())
                .inputType(SearchRequest.class)
                .build();
    }
}

package com.next.nexrailai.service;

import com.next.nexrailai.component.ThsrFunctionTools;
import com.next.nexrailai.config.PromptConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiService {

    private final ChatClient chatClient;
    private final PromptConfig promptConfig;
    private final ThsrFunctionTools thsrFunctionTools;
    private final RateLimitService rateLimitService;
    private final ChatMemory chatMemory;

    public void clearMemory(String chatId) {
        chatMemory.clear(chatId);
        log.info(">>>> [AI Service] Memory cleared for user: {}", chatId);
    }

    public String chat(String chatId, String message){
        try{
            int dailyQuota = rateLimitService.getRemainingQuota(chatId);
            int monthlyQuota = rateLimitService.getRemainingMonthlyQuota(chatId);
            
            log.info(">>>> [AI Context] User: {}, Daily: {}, Monthly: {}", chatId, dailyQuota, monthlyQuota);

            return chatClient.prompt()
                    .system(sp -> sp.text(promptConfig.getSystem().get("text"))
                            .param("today", LocalDate.now().toString())
                            .param("current_time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                            .param("daily_quota", String.valueOf(dailyQuota))
                            .param("monthly_quota", String.valueOf(monthlyQuota)))
                    .user(message)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                    .toolCallbacks(
                            thsrFunctionTools.thsrJourneySearch(),
                            thsrFunctionTools.bookTicket(),
                            thsrFunctionTools.saveUserMemory(chatId),
                            thsrFunctionTools.recallUserMemory(chatId),
                            thsrFunctionTools.addSchedule(chatId),
                            thsrFunctionTools.monitorTicket(chatId)
                    )
                    .call()
                    .content();
        }catch (Exception e){
            log.error(">>>> [AI 異常] : {}", e.getMessage(), e);
            return "哎呀 我的大腦抽筋了 \uD83E\uDD16⚡ 請稍後再試～";
        }
    }
}

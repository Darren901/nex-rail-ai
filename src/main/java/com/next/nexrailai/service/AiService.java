package com.next.nexrailai.service;

import com.next.nexrailai.dto.ThsrTimetableDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiService {

    private final ChatClient chatClient;
    private final ThsrTicketService thsrTicketService;
    private final ThsrTicketTools thsrTicketTools;

    public String chat(String chatId, String message){
        return chatClient.prompt()
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                .system(sp -> sp.text("""
                    你是一個很 Chill 的台灣高鐵助理「NexRail AI」。
                    說話風格：Gen Z、幽默、隨性。
                    今天是 {today}。
                    """)
                .param("today", LocalDate.now().toString()))
                .user(message)
                .toolCallbacks(thsrTicketTools.thsrTicketSearch(thsrTicketService))
                .call()
                .content();
    }
}

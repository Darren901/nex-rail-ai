package com.next.nexrailai.service;

import com.next.nexrailai.config.PromptConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiService {

    private final ChatClient chatClient;
    private final ThsrTicketService thsrTicketService;
    private final PromptConfig promptConfig;
    private final ThsrFunctionTools thsrFunctionTools;

    public String chat(String chatId, String message){
        return chatClient.prompt()
                .system(sp -> sp.text(promptConfig.getSystem().get("text"))
                        .param("today", LocalDate.now().toString()))
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                .toolCallbacks(thsrFunctionTools.thsrJourneySearch(thsrTicketService))
                .call()
                .content();
    }
}

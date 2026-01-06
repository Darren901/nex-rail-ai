package com.next.nexrailai.service;

import com.next.nexrailai.jpa.entity.UserMemory;
import com.next.nexrailai.jpa.repository.UserMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserMemoryService {

    private final UserMemoryRepository memoryRepo;

    @Transactional
    public String saveMemory(String userId, String key, String value) {
        log.info(">>>> [記憶服務] 儲存記憶: User={}, Key={}, Value={}", userId, key, value);
        
        UserMemory memory = memoryRepo.findByUserIdAndMemoryKey(userId, key)
                .orElse(new UserMemory());
        
        memory.setUserId(userId);
        memory.setMemoryKey(key);
        memory.setMemoryValue(value);
        
        memoryRepo.save(memory);
        return "已成功記住關於「" + key + "」的資訊。";
    }

    public String recallMemory(String userId, String key) {
        log.info(">>>> [記憶服務] 提取記憶: User={}, Key={}", userId, key);
        
        return memoryRepo.findByUserIdAndMemoryKey(userId, key)
                .map(UserMemory::getMemoryValue)
                .orElse("找不到關於「" + key + "」的任何記憶。");
    }
}

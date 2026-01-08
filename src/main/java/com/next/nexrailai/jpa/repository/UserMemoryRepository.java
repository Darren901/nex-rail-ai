package com.next.nexrailai.jpa.repository;

import com.next.nexrailai.jpa.entity.UserMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserMemoryRepository extends JpaRepository<UserMemory, Long> {
    Optional<UserMemory> findByUserIdAndMemoryKey(String userId, String memoryKey);
    java.util.List<UserMemory> findByUserId(String userId);
}

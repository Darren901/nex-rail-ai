package com.next.nexrailai.jpa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_memories", indexes = {
        @Index(name = "idx_user_memory_key", columnList = "userId, memoryKey")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String memoryKey;

    @Column(columnDefinition = "TEXT")
    private String memoryValue; // 存儲 JSON 字串

    @CreationTimestamp
    private LocalDateTime createdAt;
}

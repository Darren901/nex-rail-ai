package com.next.nexrailai.jpa.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "schedule_tasks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "trigger_time", nullable = false)
    private LocalDateTime triggerTime;

    @Column(name = "content", length = 1000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false)
    private TaskType taskType;

    @Column(name = "payload", length = 2000)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TaskStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = TaskStatus.PENDING;
        }
        if (this.taskType == null) {
            this.taskType = TaskType.REMINDER;
        }
    }

    public enum TaskStatus {
        PENDING,
        EXECUTED,
        COMPLETED, // 任務完成 (例如監控到有票)
        CANCELLED,
        FAILED,
        EXPIRED    // 任務過期 (例如已過發車時間)
    }

    public enum TaskType {
        REMINDER,      // 純文字提醒
        TICKET_MONITOR // 查票監控
    }
}

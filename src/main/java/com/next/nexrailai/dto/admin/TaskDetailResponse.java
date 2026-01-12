package com.next.nexrailai.dto.admin;

import com.next.nexrailai.jpa.entity.ScheduleTask;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TaskDetailResponse {
    private Long id;
    private String userId;
    private String userDisplayName;
    private String userPictureUrl;
    private LocalDateTime triggerTime;
    private String content;
    private ScheduleTask.TaskType taskType;
    private String payload;
    private ScheduleTask.TaskStatus status;
    private LocalDateTime createdAt;

    public static TaskDetailResponse fromEntity(ScheduleTask task, String displayName, String pictureUrl) {
        return TaskDetailResponse.builder()
                .id(task.getId())
                .userId(task.getUserId())
                .userDisplayName(displayName)
                .userPictureUrl(pictureUrl)
                .triggerTime(task.getTriggerTime())
                .content(task.getContent())
                .taskType(task.getTaskType())
                .payload(task.getPayload())
                .status(task.getStatus())
                .createdAt(task.getCreatedAt())
                .build();
    }
}

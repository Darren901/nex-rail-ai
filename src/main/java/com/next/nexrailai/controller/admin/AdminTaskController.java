package com.next.nexrailai.controller.admin;

import com.next.nexrailai.dto.admin.TaskDetailResponse;
import com.next.nexrailai.jpa.entity.AppUser;
import com.next.nexrailai.jpa.entity.ScheduleTask;
import com.next.nexrailai.jpa.repository.AppUserRepository;
import com.next.nexrailai.jpa.repository.ScheduleTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/tasks")
@RequiredArgsConstructor
public class AdminTaskController {

    private final ScheduleTaskRepository taskRepository;
    private final AppUserRepository userRepository;

    @GetMapping
    public ResponseEntity<Page<TaskDetailResponse>> getTasks(
            @RequestParam(defaultValue = "PENDING") ScheduleTask.TaskStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "triggerTime"));
        
        Page<ScheduleTask> taskPage = taskRepository.findByStatus(status, pageRequest);

        // 收集所有 User IDs
        Set<String> userIds = taskPage.getContent().stream()
                .map(ScheduleTask::getUserId)
                .collect(Collectors.toSet());

        // 查詢 User Info
        Map<String, AppUser> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(AppUser::getLineUserId, Function.identity()));

        // 轉換為 DTO
        Page<TaskDetailResponse> responsePage = taskPage.map(task -> {
            AppUser user = userMap.get(task.getUserId());
            String displayName = (user != null) ? user.getDisplayName() : "Unknown";
            String pictureUrl = (user != null) ? user.getPictureUrl() : null;
            return TaskDetailResponse.fromEntity(task, displayName, pictureUrl);
        });

        return ResponseEntity.ok(responsePage);
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> cancelTask(@PathVariable Long taskId) {
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setStatus(ScheduleTask.TaskStatus.CANCELLED);
            taskRepository.save(task);
        });
        return ResponseEntity.ok().build();
    }
}

package com.next.nexrailai.controller.admin;

import com.next.nexrailai.jpa.entity.ScheduleTask;
import com.next.nexrailai.jpa.repository.ScheduleTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/tasks")
@RequiredArgsConstructor
public class AdminTaskController {

    private final ScheduleTaskRepository taskRepository;

    @GetMapping
    public ResponseEntity<Page<ScheduleTask>> getTasks(
            @RequestParam(defaultValue = "PENDING") ScheduleTask.TaskStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "triggerTime"));
        return ResponseEntity.ok(taskRepository.findByStatus(status, pageRequest));
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

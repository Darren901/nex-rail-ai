package com.next.nexrailai.jpa.repository;

import com.next.nexrailai.jpa.entity.ScheduleTask;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ScheduleTaskRepository extends JpaRepository<ScheduleTask, Long> {
    
    // 找出所有時間到了且狀態為 PENDING 的任務
    List<ScheduleTask> findByStatusAndTriggerTimeBefore(ScheduleTask.TaskStatus status, LocalDateTime time);

    // 找出某使用者的所有未完成任務 (給使用者查詢用)
    List<ScheduleTask> findByUserIdAndStatus(String userId, ScheduleTask.TaskStatus status);

    // 清理舊資料
    void deleteByStatusInAndCreatedAtBefore(List<ScheduleTask.TaskStatus> statuses, LocalDateTime time);

    // 後台管理用：分頁查詢特定狀態的任務
    Page<ScheduleTask> findByStatus(ScheduleTask.TaskStatus status, org.springframework.data.domain.Pageable pageable);
}

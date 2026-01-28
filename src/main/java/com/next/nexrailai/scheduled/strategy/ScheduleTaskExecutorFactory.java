package com.next.nexrailai.scheduled.strategy;

import com.next.nexrailai.common.ApBusinessException;
import com.next.nexrailai.common.Constant;
import com.next.nexrailai.jpa.entity.ScheduleTask;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ScheduleTaskExecutorFactory {

    private final List<ScheduleTaskExecutor> executors;
    private Map<ScheduleTask.TaskType, ScheduleTaskExecutor> executorMap;

    @PostConstruct
    public void init() {
        this.executorMap = executors.stream()
                .collect(Collectors.toMap(ScheduleTaskExecutor::getSupportedTaskType, Function.identity()));
    }

    public ScheduleTaskExecutor getExecutor(ScheduleTask.TaskType type) {
        return Optional.ofNullable(executorMap.get(type))
                .orElseThrow(() -> new ApBusinessException(Constant.RCODE.TASK_EXECUTOR_NOT_FOUND));
    }
}

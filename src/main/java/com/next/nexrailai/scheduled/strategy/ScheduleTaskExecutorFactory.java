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

    /**
     * Builds a map from each executor's supported task type to the corresponding executor.
     *
     * Executed after dependency injection to initialize the internal lookup used to resolve a ScheduleTaskExecutor by ScheduleTask.TaskType.
     *
     * @throws IllegalStateException if multiple executors report the same supported task type
     */
    @PostConstruct
    public void init() {
        this.executorMap = executors.stream()
                .collect(Collectors.toMap(ScheduleTaskExecutor::getSupportedTaskType, Function.identity()));
    }

    /**
     * Locate the ScheduleTaskExecutor that handles the given task type.
     *
     * @param type the task type to resolve
     * @return the executor that handles the specified task type
     * @throws ApBusinessException when no executor is registered for the provided task type (RCODE.TASK_EXECUTOR_NOT_FOUND)
     */
    public ScheduleTaskExecutor getExecutor(ScheduleTask.TaskType type) {
        return Optional.ofNullable(executorMap.get(type))
                .orElseThrow(() -> new ApBusinessException(Constant.RCODE.TASK_EXECUTOR_NOT_FOUND));
    }
}
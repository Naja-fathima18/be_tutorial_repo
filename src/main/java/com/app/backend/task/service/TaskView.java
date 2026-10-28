package com.app.backend.task.service;

import com.app.backend.task.domain.Task;
import com.app.backend.task.domain.TaskStatus;
import java.time.Instant;
import java.time.LocalDate;

public record TaskView(
        Long id,
        String title,
        String description,
        TaskStatus status,
        LocalDate dueDate,
        Instant createdAt,
        Instant updatedAt,
        Long version) {

    static TaskView from(Task task) {
        return new TaskView(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getDueDate(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getVersion());
    }
}

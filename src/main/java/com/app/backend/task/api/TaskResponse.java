package com.app.backend.task.api;

import com.app.backend.task.domain.TaskStatus;
import com.app.backend.task.service.TaskView;
import java.time.Instant;
import java.time.LocalDate;

public record TaskResponse(
        Long id,
        String title,
        String description,
        TaskStatus status,
        LocalDate dueDate,
        Instant createdAt,
        Instant updatedAt,
        Long version) {

    static TaskResponse from(TaskView view) {
        return new TaskResponse(
                view.id(),
                view.title(),
                view.description(),
                view.status(),
                view.dueDate(),
                view.createdAt(),
                view.updatedAt(),
                view.version());
    }
}

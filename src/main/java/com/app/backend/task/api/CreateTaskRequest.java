package com.app.backend.task.api;

import com.app.backend.task.domain.TaskStatus;
import com.app.backend.task.service.TaskCommand;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateTaskRequest(
        @NotBlank @Size(max = 100) String title,
        @Size(max = 2000) String description,
        TaskStatus status,
        @FutureOrPresent LocalDate dueDate) {

    TaskCommand toCommand() {
        return new TaskCommand(title, description, status, dueDate);
    }
}

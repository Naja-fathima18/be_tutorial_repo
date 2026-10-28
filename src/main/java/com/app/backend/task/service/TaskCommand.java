package com.app.backend.task.service;

import com.app.backend.task.domain.TaskStatus;
import java.time.LocalDate;

public record TaskCommand(String title, String description, TaskStatus status, LocalDate dueDate) {}

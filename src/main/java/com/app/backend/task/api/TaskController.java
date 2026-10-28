package com.app.backend.task.api;

import com.app.backend.common.api.PageResponse;
import com.app.backend.task.domain.TaskStatus;
import com.app.backend.task.service.TaskService;
import com.app.backend.task.service.TaskView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request) {
        TaskView created = taskService.create(request.toCommand());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(TaskResponse.from(created));
    }

    @GetMapping
    public PageResponse<TaskResponse> list(
            @RequestParam(required = false) TaskStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(taskService.list(status, pageable).map(TaskResponse::from));
    }

    @GetMapping("/{id}")
    public TaskResponse get(@PathVariable @Positive long id) {
        return TaskResponse.from(taskService.get(id));
    }

    @PutMapping("/{id}")
    public TaskResponse update(@PathVariable @Positive long id, @Valid @RequestBody UpdateTaskRequest request) {
        return TaskResponse.from(taskService.update(id, request.toCommand()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive long id) {
        taskService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

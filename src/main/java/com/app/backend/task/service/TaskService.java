package com.app.backend.task.service;

import com.app.backend.common.error.BadRequestException;
import com.app.backend.common.error.NotFoundException;
import com.app.backend.task.domain.Task;
import com.app.backend.task.domain.TaskStatus;
import com.app.backend.task.persistence.TaskRepository;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    static final int MAX_PAGE_SIZE = 100;
    static final Set<String> SORTABLE_PROPERTIES = Set.of("id", "title", "status", "dueDate", "createdAt", "updatedAt");

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Transactional
    public TaskView create(TaskCommand command) {
        TaskStatus status = command.status() == null ? TaskStatus.TODO : command.status();
        Task task = new Task(command.title(), command.description(), status, command.dueDate());
        Task saved = taskRepository.saveAndFlush(task);
        log.info("Created task {}", saved.getId());
        return TaskView.from(saved);
    }

    @Transactional(readOnly = true)
    public TaskView get(long id) {
        return TaskView.from(findTask(id));
    }

    @Transactional(readOnly = true)
    public Page<TaskView> list(TaskStatus status, Pageable pageable) {
        Pageable bounded = bounded(pageable);
        Page<Task> page =
                status == null ? taskRepository.findAll(bounded) : taskRepository.findByStatus(status, bounded);
        return page.map(TaskView::from);
    }

    @Transactional
    public TaskView update(long id, TaskCommand command) {
        Task task = findTask(id);
        task.update(command.title(), command.description(), command.status(), command.dueDate());
        Task saved = taskRepository.saveAndFlush(task);
        log.info("Updated task {}", id);
        return TaskView.from(saved);
    }

    @Transactional
    public void delete(long id) {
        Task task = findTask(id);
        taskRepository.delete(task);
        log.info("Deleted task {}", id);
    }

    private Task findTask(long id) {
        return taskRepository.findById(id).orElseThrow(() -> new NotFoundException("Task " + id + " not found"));
    }

    private static Pageable bounded(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw new BadRequestException("Unsupported sort property: " + order.getProperty());
            }
        }
        int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
        return PageRequest.of(pageable.getPageNumber(), size, pageable.getSort());
    }
}

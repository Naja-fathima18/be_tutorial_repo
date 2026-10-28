package com.app.backend.task.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.backend.task.domain.Task;
import com.app.backend.task.domain.TaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@DataJpaTest
class TaskRepositoryTest {

    @Autowired
    private TaskRepository taskRepository;

    @Test
    void findsOnlyTasksWithRequestedStatusPaged() {
        taskRepository.saveAndFlush(new Task("a", null, TaskStatus.DONE, null));
        taskRepository.saveAndFlush(new Task("b", null, TaskStatus.TODO, null));
        taskRepository.saveAndFlush(new Task("c", null, TaskStatus.DONE, null));
        taskRepository.saveAndFlush(new Task("d", null, TaskStatus.DONE, null));

        Page<Task> firstPage = taskRepository.findByStatus(TaskStatus.DONE, PageRequest.of(0, 2, Sort.by("title")));

        assertThat(firstPage.getContent()).extracting(Task::getTitle).containsExactly("a", "c");
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
    }

    @Test
    void setsAuditColumnsAndVersionOnSave() {
        Task saved = taskRepository.saveAndFlush(new Task("a", null, TaskStatus.TODO, null));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getVersion()).isZero();
    }
}

package com.app.backend.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.app.backend.common.error.BadRequestException;
import com.app.backend.common.error.NotFoundException;
import com.app.backend.task.domain.Task;
import com.app.backend.task.domain.TaskStatus;
import com.app.backend.task.persistence.TaskRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskService taskService;

    @Test
    void createsTaskWithTodoStatusWhenStatusIsAbsent() {
        given(taskRepository.saveAndFlush(any(Task.class))).willAnswer(invocation -> invocation.getArgument(0));
        LocalDate dueDate = LocalDate.now().plusDays(2);

        TaskView created = taskService.create(new TaskCommand("Plan", "desc", null, dueDate));

        assertThat(created.status()).isEqualTo(TaskStatus.TODO);
        assertThat(created.title()).isEqualTo("Plan");
        assertThat(created.dueDate()).isEqualTo(dueDate);
    }

    @Test
    void createsTaskWithRequestedStatus() {
        given(taskRepository.saveAndFlush(any(Task.class))).willAnswer(invocation -> invocation.getArgument(0));

        TaskView created = taskService.create(new TaskCommand("Plan", null, TaskStatus.IN_PROGRESS, null));

        assertThat(created.status()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void throwsNotFoundWhenTaskIsUnknown() {
        given(taskRepository.findById(42L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.get(42L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Task 42 not found");
    }

    @Test
    void updatesAllFieldsOfExistingTask() {
        Task existing = new Task("Old", "old", TaskStatus.TODO, null);
        given(taskRepository.findById(1L)).willReturn(Optional.of(existing));
        given(taskRepository.saveAndFlush(existing)).willReturn(existing);
        LocalDate dueDate = LocalDate.now().plusDays(1);

        TaskView updated = taskService.update(1L, new TaskCommand("New", null, TaskStatus.DONE, dueDate));

        assertThat(updated.title()).isEqualTo("New");
        assertThat(updated.description()).isNull();
        assertThat(updated.status()).isEqualTo(TaskStatus.DONE);
        assertThat(updated.dueDate()).isEqualTo(dueDate);
    }

    @Test
    void doesNotSaveWhenUpdatingUnknownTask() {
        given(taskRepository.findById(9L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.update(9L, new TaskCommand("x", null, TaskStatus.TODO, null)))
                .isInstanceOf(NotFoundException.class);

        then(taskRepository).should(never()).saveAndFlush(any());
    }

    @Test
    void deletesExistingTask() {
        Task existing = new Task("Old", null, TaskStatus.TODO, null);
        given(taskRepository.findById(1L)).willReturn(Optional.of(existing));

        taskService.delete(1L);

        then(taskRepository).should().delete(existing);
    }

    @Test
    void throwsNotFoundWhenDeletingUnknownTask() {
        given(taskRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.delete(1L)).isInstanceOf(NotFoundException.class);

        then(taskRepository).should(never()).delete(any());
    }

    @Test
    void filtersByStatusWhenProvided() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Task> page = new PageImpl<>(List.of(new Task("a", null, TaskStatus.DONE, null)), pageable, 1);
        given(taskRepository.findByStatus(TaskStatus.DONE, pageable)).willReturn(page);

        Page<TaskView> result = taskService.list(TaskStatus.DONE, pageable);

        assertThat(result.getContent()).extracting(TaskView::status).containsExactly(TaskStatus.DONE);
        then(taskRepository).should(never()).findAll(any(Pageable.class));
    }

    @Test
    void listsAllWhenStatusIsAbsent() {
        Pageable pageable = PageRequest.of(0, 10);
        given(taskRepository.findAll(pageable)).willReturn(Page.empty(pageable));

        Page<TaskView> result = taskService.list(null, pageable);

        assertThat(result.getContent()).isEmpty();
        then(taskRepository).should(never()).findByStatus(any(), any());
    }

    @Test
    void capsPageSizeAtMaximum() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        given(taskRepository.findAll(captor.capture())).willReturn(Page.empty());

        taskService.list(null, PageRequest.of(2, 500, Sort.by("title")));

        Pageable used = captor.getValue();
        assertThat(used.getPageSize()).isEqualTo(TaskService.MAX_PAGE_SIZE);
        assertThat(used.getPageNumber()).isEqualTo(2);
        assertThat(used.getSort()).isEqualTo(Sort.by("title"));
    }

    @Test
    void rejectsUnsupportedSortProperty() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("password"));

        assertThatThrownBy(() -> taskService.list(null, pageable))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Unsupported sort property: password");

        then(taskRepository).shouldHaveNoInteractions();
    }
}

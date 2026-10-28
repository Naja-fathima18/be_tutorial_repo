package com.app.backend.task.api;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.backend.common.error.NotFoundException;
import com.app.backend.task.domain.TaskStatus;
import com.app.backend.task.service.TaskCommand;
import com.app.backend.task.service.TaskService;
import com.app.backend.task.service.TaskView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TaskController.class)
@AutoConfigureMockMvc(addFilters = false)
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskService taskService;

    private static TaskView view(long id, TaskStatus status) {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        return new TaskView(
                id, "Write tests", "details", status, LocalDate.now().plusDays(1), now, now, 0L);
    }

    @Test
    void createsTaskAndReturnsLocation() throws Exception {
        given(taskService.create(any(TaskCommand.class))).willReturn(view(7L, TaskStatus.TODO));
        String body = """
                {"title":"Write tests","description":"details","dueDate":"%s"}
                """.formatted(LocalDate.now().plusDays(1));

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/tasks/7")))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.createdAt").value("2026-01-01T10:00:00Z"));

        then(taskService)
                .should()
                .create(new TaskCommand(
                        "Write tests", "details", null, LocalDate.now().plusDays(1)));
    }

    @Test
    void rejectsCreateWithFieldErrorForEveryInvalidField() throws Exception {
        String body = """
                {"title":"   ","dueDate":"%s","description":"%s"}
                """.formatted(LocalDate.now().minusDays(1), "d".repeat(2001));

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.instance").value("/api/tasks"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.errors", hasSize(3)))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("title", "dueDate", "description")));

        then(taskService).shouldHaveNoInteractions();
    }

    @Test
    void rejectsTitleLongerThanOneHundredCharacters() throws Exception {
        String body = """
                {"title":"%s"}
                """.formatted("t".repeat(101));

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("title"));
    }

    @Test
    void acceptsTitleOfExactlyOneHundredCharactersAndDueDateToday() throws Exception {
        given(taskService.create(any(TaskCommand.class))).willReturn(view(1L, TaskStatus.TODO));
        String body = """
                {"title":"%s","dueDate":"%s"}
                """.formatted("t".repeat(100), LocalDate.now());

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsInvalidStatusInBodyWithFieldError() throws Exception {
        String body = """
                {"title":"ok","status":"BLOCKED"}
                """;

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("status"))
                .andExpect(jsonPath("$.errors[0].message").value("must be one of [TODO, IN_PROGRESS, DONE]"));
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Malformed JSON request"));
    }

    @Test
    void returnsTaskById() throws Exception {
        given(taskService.get(3L)).willReturn(view(3L, TaskStatus.IN_PROGRESS));

        mockMvc.perform(get("/api/tasks/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void returnsNotFoundProblemForUnknownTask() throws Exception {
        given(taskService.get(99L)).willThrow(new NotFoundException("Task 99 not found"));

        mockMvc.perform(get("/api/tasks/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("Task 99 not found"))
                .andExpect(jsonPath("$.instance").value("/api/tasks/99"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void rejectsNonNumericId() throws Exception {
        mockMvc.perform(get("/api/tasks/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("id"));
    }

    @Test
    void rejectsNonPositiveId() throws Exception {
        mockMvc.perform(get("/api/tasks/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("id"));

        then(taskService).shouldHaveNoInteractions();
    }

    @Test
    void listsTasksFilteredByStatusWithPagination() throws Exception {
        given(taskService.list(eq(TaskStatus.DONE), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(view(1L, TaskStatus.DONE)), PageRequest.of(1, 5), 6));

        mockMvc.perform(get("/api/tasks")
                        .param("status", "DONE")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].status").value("DONE"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void listsAllTasksWhenStatusIsAbsent() throws Exception {
        given(taskService.list(isNull(), any(Pageable.class))).willReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/tasks")).andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void rejectsInvalidStatusFilter() throws Exception {
        mockMvc.perform(get("/api/tasks").param("status", "BLOCKED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("status"))
                .andExpect(jsonPath("$.errors[0].message").value("must be one of [TODO, IN_PROGRESS, DONE]"));
    }

    @Test
    void updatesTask() throws Exception {
        given(taskService.update(eq(4L), any(TaskCommand.class))).willReturn(view(4L, TaskStatus.DONE));
        String body = """
                {"title":"Write tests","status":"DONE"}
                """;

        mockMvc.perform(put("/api/tasks/4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));

        then(taskService).should().update(4L, new TaskCommand("Write tests", null, TaskStatus.DONE, null));
    }

    @Test
    void rejectsUpdateWithMissingStatusAndPastDueDate() throws Exception {
        String body = """
                {"title":"ok","dueDate":"%s"}
                """.formatted(LocalDate.now().minusDays(1));

        mockMvc.perform(put("/api/tasks/4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("status", "dueDate")));

        then(taskService).shouldHaveNoInteractions();
    }

    @Test
    void returnsNotFoundWhenUpdatingUnknownTask() throws Exception {
        given(taskService.update(eq(99L), any(TaskCommand.class)))
                .willThrow(new NotFoundException("Task 99 not found"));
        String body = """
                {"title":"ok","status":"TODO"}
                """;

        mockMvc.perform(put("/api/tasks/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletesTask() throws Exception {
        mockMvc.perform(delete("/api/tasks/5")).andExpect(status().isNoContent());

        then(taskService).should().delete(5L);
    }

    @Test
    void returnsNotFoundWhenDeletingUnknownTask() throws Exception {
        willThrow(new NotFoundException("Task 99 not found")).given(taskService).delete(99L);

        mockMvc.perform(delete("/api/tasks/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Task 99 not found"));
    }

    @Test
    void returnsInternalServerErrorProblemWithoutDetailsOfUnexpectedFailure() throws Exception {
        given(taskService.get(1L)).willThrow(new IllegalStateException("db password is hunter2"));

        mockMvc.perform(get("/api/tasks/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());
    }
}

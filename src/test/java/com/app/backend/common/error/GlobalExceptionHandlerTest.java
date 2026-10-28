package com.app.backend.common.error;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.ProbeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandlerTest.ProbeController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @RestController
    static class ProbeController {

        record Probe(@Min(1) int value) {}

        @GetMapping("/probe/gone")
        String gone() {
            throw new ApiException(HttpStatus.GONE, "Link expired");
        }

        @GetMapping("/probe/conflict")
        String conflict() {
            throw new ConflictException("Already exists");
        }

        @GetMapping("/probe/violation")
        String violation() {
            Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
            throw new ConstraintViolationException(validator.validate(new Probe(0)));
        }

        @GetMapping("/probe/param")
        String param(@RequestParam @Min(1) int limit) {
            return "ok";
        }

        @GetMapping("/probe/optimistic-lock")
        String optimisticLock() {
            throw new ObjectOptimisticLockingFailureException(Probe.class, 1L);
        }

        @PostMapping("/probe/ranges")
        String range(@Valid @RequestBody Range range) {
            return "ok";
        }

        @PostMapping("/probe/ranges/{id}")
        String rangeWithId(@PathVariable @Positive long id, @Valid @RequestBody Range range) {
            return "ok";
        }
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = OrderedRangeValidator.class)
    @interface OrderedRange {
        String message() default "start must not be after end";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    static class OrderedRangeValidator implements ConstraintValidator<OrderedRange, Range> {

        @Override
        public boolean isValid(Range range, ConstraintValidatorContext context) {
            return range.start() <= range.end();
        }
    }

    @OrderedRange
    record Range(int start, int end) {}

    @Test
    void mapsOptimisticLockFailureTo409() throws Exception {
        mockMvc.perform(get("/probe/optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.instance").value("/probe/optimistic-lock"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void mapsClassLevelConstraintOnBodyToError() throws Exception {
        mockMvc.perform(post("/probe/ranges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"start\":5,\"end\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].message").value("start must not be after end"));
    }

    @Test
    void mapsClassLevelConstraintOnBodyToErrorWhenPathVariableIsConstrained() throws Exception {
        mockMvc.perform(post("/probe/ranges/{id}", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"start\":5,\"end\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].message").value("start must not be after end"));
    }

    @Test
    void mapsPathVariableAndClassLevelBodyErrorsTogether() throws Exception {
        mockMvc.perform(post("/probe/ranges/{id}", 0)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"start\":5,\"end\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[*].field", hasItem("id")));
    }

    @Test
    void mapsApiExceptionToItsStatus() throws Exception {
        mockMvc.perform(get("/probe/gone"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.detail").value("Link expired"))
                .andExpect(jsonPath("$.instance").value("/probe/gone"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void mapsConflictExceptionTo409() throws Exception {
        mockMvc.perform(get("/probe/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("Already exists"));
    }

    @Test
    void mapsConstraintViolationExceptionToFieldErrors() throws Exception {
        mockMvc.perform(get("/probe/violation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("value"))
                .andExpect(jsonPath("$.errors[0].message").exists());
    }

    @Test
    void mapsConstraintViolationOnRequestParamToFieldErrors() throws Exception {
        mockMvc.perform(get("/probe/param").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("limit"));
    }

    @Test
    void mapsMissingRequestParamTo400Problem() throws Exception {
        mockMvc.perform(get("/probe/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void returnsNotFoundProblemForUnknownRoute() throws Exception {
        mockMvc.perform(get("/does/not/exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.instance").value("/does/not/exist"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}

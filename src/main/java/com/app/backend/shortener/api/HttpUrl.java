package com.app.backend.shortener.api;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = HttpUrlValidator.class)
public @interface HttpUrl {

    String message() default "must be an absolute http or https URL with a host";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

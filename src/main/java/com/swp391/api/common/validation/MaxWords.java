package com.swp391.api.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaxWordsValidator.class)
public @interface MaxWords {
    String message() default "Text contains too many words";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    int value();
}

package com.swp391.api.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MaxWordsValidator implements ConstraintValidator<MaxWords, String> {
    private int maximum;

    @Override
    public void initialize(MaxWords constraint) {
        maximum = constraint.value();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) return true;
        return value.trim().split("\\s+").length <= maximum;
    }
}

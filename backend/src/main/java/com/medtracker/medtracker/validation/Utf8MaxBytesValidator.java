package com.medtracker.medtracker.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class Utf8MaxBytesValidator implements ConstraintValidator<Utf8MaxBytes, CharSequence> {

    private int max;

    @Override
    public void initialize(Utf8MaxBytes annotation) {
        this.max = annotation.value();
    }

    // null es válido: la obligatoriedad la decide @NotNull / @NotBlank
    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        // Cada carácter ocupa al menos un byte: más caracteres que el tope ya
        // lo excede, sin copiar un texto gigante a bytes
        return value.length() <= max && value.toString().getBytes(StandardCharsets.UTF_8).length <= max;
    }
}

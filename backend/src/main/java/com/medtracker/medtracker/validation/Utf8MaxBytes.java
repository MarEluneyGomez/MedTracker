package com.medtracker.medtracker.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

// El texto no puede ocupar más de "value" bytes en UTF-8. @Size cuenta
// caracteres, y una tilde o una eñe ocupan 2 bytes: BCrypt rechaza lo que
// supere 72 bytes, así que la contraseña se acota en bytes.
@Documented
@Constraint(validatedBy = Utf8MaxBytesValidator.class)
@Target(FIELD)
@Retention(RUNTIME)
public @interface Utf8MaxBytes {

    String message() default "supera el largo permitido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    int value();
}

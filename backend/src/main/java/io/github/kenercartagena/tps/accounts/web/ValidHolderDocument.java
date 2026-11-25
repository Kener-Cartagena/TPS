package io.github.kenercartagena.tps.accounts.web;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The document of a {@link RegisterAccountHolderRequest} is valid for its type (e.g. a DNI
 * has 13 digits) and for the holder type (a business uses its RTN). Violations are
 * reported on the {@code documentNumber} or {@code documentType} field.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = HolderDocumentValidator.class)
public @interface ValidHolderDocument {

    String message() default "is not a valid identity document";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

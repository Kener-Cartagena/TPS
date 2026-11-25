package io.github.kenercartagena.tps.accounts.web;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Checks {@link ValidHolderDocument}. Missing fields are left to {@code @NotNull} and
 * {@code @NotBlank}, so each problem is reported once.
 */
public class HolderDocumentValidator
        implements ConstraintValidator<ValidHolderDocument, RegisterAccountHolderRequest> {

    @Override
    public boolean isValid(RegisterAccountHolderRequest request, ConstraintValidatorContext context) {
        if (request == null || request.documentType() == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        boolean valid = true;
        if (request.holderType() != null && !request.holderType().accepts(request.documentType())) {
            violation(context, "documentType", "a business must be identified by its RTN");
            valid = false;
        }
        if (request.documentNumber() != null && !request.documentNumber().isBlank()
                && !request.documentType().isValid(request.documentNumber())) {
            violation(context, "documentNumber", request.documentType().formatDescription());
            valid = false;
        }
        return valid;
    }

    private static void violation(ConstraintValidatorContext context, String field, String message) {
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(field)
                .addConstraintViolation();
    }
}

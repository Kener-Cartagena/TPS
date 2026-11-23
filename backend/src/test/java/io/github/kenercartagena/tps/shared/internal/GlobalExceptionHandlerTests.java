package io.github.kenercartagena.tps.shared.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kenercartagena.tps.shared.error.BusinessRuleViolationException;
import io.github.kenercartagena.tps.shared.error.ErrorCode;
import io.github.kenercartagena.tps.shared.error.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verifies that every kind of failure produces a consistent RFC 9457 Problem Details
 * response. Uses a controller that exists only in this test, so no fake endpoint is
 * shipped with the application.
 */
@WebMvcTest
@Import(GlobalExceptionHandlerTests.ErrorTestController.class)
class GlobalExceptionHandlerTests {

    private static final String BASE = "/test/errors";

    @Autowired
    private MockMvcTester mvc;

    @Test
    void invalidRequestBodyReturnsFieldViolations() {
        MvcTestResult result = mvc.post().uri(BASE + "/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": " ", "amount": -5}
                        """)
                .exchange();

        assertProblem(result, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Request validation failed.");
        assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray()
                .containsExactlyInAnyOrder("name", "amount");
    }

    @Test
    void validationErrorsNeverEchoTheRejectedValue() {
        MvcTestResult result = mvc.post().uri(BASE + "/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "ok", "amount": -98765}
                        """)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyText().doesNotContain("98765");
    }

    @Test
    void invalidRequestParameterReturnsFieldViolations() {
        MvcTestResult result = mvc.get().uri(BASE + "/parameter?size=0").exchange();

        assertProblem(result, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("size");
    }

    @Test
    void malformedJsonReturnsMalformedRequest() {
        MvcTestResult result = mvc.post().uri(BASE + "/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ not json")
                .exchange();

        assertProblem(result, HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST");
    }

    @Test
    void resourceNotFoundReturns404WithModuleErrorCode() {
        MvcTestResult result = mvc.get().uri(BASE + "/not-found").exchange();

        assertProblem(result, HttpStatus.NOT_FOUND, "SAMPLE_NOT_FOUND");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Sample 'abc' was not found.");
        assertThat(result).bodyJson().extractingPath("$.instance").isEqualTo(BASE + "/not-found");
    }

    @Test
    void businessRuleViolationReturns422() {
        MvcTestResult result = mvc.get().uri(BASE + "/business-rule").exchange();

        assertProblem(result, HttpStatus.UNPROCESSABLE_CONTENT, "SAMPLE_RULE_BROKEN");
    }

    @Test
    void unknownEndpointReturns404NotFound() {
        MvcTestResult result = mvc.get().uri("/does-not-exist").exchange();

        assertProblem(result, HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertThat(result).bodyJson().extractingPath("$.detail")
                .isEqualTo("The requested endpoint does not exist.");
    }

    @Test
    void wrongHttpMethodReturns405() {
        MvcTestResult result = mvc.delete().uri(BASE + "/not-found").exchange();

        assertProblem(result, HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED");
    }

    @Test
    void unsupportedContentTypeReturns415() {
        MvcTestResult result = mvc.post().uri(BASE + "/validation")
                .contentType(MediaType.TEXT_PLAIN)
                .content("hello")
                .exchange();

        assertProblem(result, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void unexpectedErrorReturnsGeneric500WithoutInternalDetails() {
        MvcTestResult result = mvc.get().uri(BASE + "/unexpected").exchange();

        assertProblem(result, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
        assertThat(result).bodyText()
                .doesNotContain("secret-internal-detail")
                .doesNotContain("IllegalStateException");
    }

    @Test
    void errorResponseCarriesTheSameRequestIdAsTheResponseHeader() {
        MvcTestResult result = mvc.get().uri(BASE + "/not-found")
                .header(RequestIdFilter.HEADER, "trace-42")
                .exchange();

        assertThat(result).hasHeader(RequestIdFilter.HEADER, "trace-42");
        assertThat(result).bodyJson().extractingPath("$.requestId").isEqualTo("trace-42");
    }

    /** Common assertions for every Problem Details response. */
    private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(status.value());
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo(status.getReasonPhrase());
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
        assertThat(result).bodyJson().extractingPath("$.requestId").asString().isNotBlank();
        assertThat(result).bodyJson().extractingPath("$.timestamp").asString().isNotBlank();
    }

    // ---- Test-only fixtures ------------------------------------------------------

    enum TestErrorCode implements ErrorCode {
        SAMPLE_NOT_FOUND, SAMPLE_RULE_BROKEN;

        @Override
        public String code() {
            return name();
        }
    }

    record SampleRequest(@NotBlank String name, @NotNull @Positive BigDecimal amount) {
    }

    @RestController
    @RequestMapping(BASE)
    static class ErrorTestController {

        @PostMapping("/validation")
        SampleRequest validation(@Valid @RequestBody SampleRequest request) {
            return request;
        }

        @GetMapping("/parameter")
        int parameter(@RequestParam @Min(1) int size) {
            return size;
        }

        @GetMapping("/not-found")
        void notFound() {
            throw new ResourceNotFoundException(TestErrorCode.SAMPLE_NOT_FOUND, "Sample 'abc' was not found.");
        }

        @GetMapping("/business-rule")
        void businessRule() {
            throw new BusinessRuleViolationException(TestErrorCode.SAMPLE_RULE_BROKEN, "The rule was broken.");
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("secret-internal-detail");
        }
    }
}

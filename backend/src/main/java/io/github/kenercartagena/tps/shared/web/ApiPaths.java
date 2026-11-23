package io.github.kenercartagena.tps.shared.web;

/**
 * Base paths of the public REST API. Controllers build their mappings from these
 * constants, e.g. {@code @RequestMapping(ApiPaths.API_V1 + "/accounts")}.
 */
public final class ApiPaths {

    /** Version 1 of the public REST API. */
    public static final String API_V1 = "/api/v1";

    private ApiPaths() {
    }
}

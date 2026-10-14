package de.legitserver.assistant.api.v1.controller;

import de.legitserver.assistant.api.v1.model.ApiCallContext;
import jakarta.servlet.http.HttpServletRequest;

/** Narrow bridge for sibling API packages; validation logic remains centralized in ApiControllerSupport. */
public final class ApiControllerSupportBridge {
    private ApiControllerSupportBridge() {}

    public static ApiCallContext context(HttpServletRequest request) {
        return ApiControllerSupport.context(request);
    }
}

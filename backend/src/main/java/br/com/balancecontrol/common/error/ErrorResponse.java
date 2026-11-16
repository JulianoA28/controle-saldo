package br.com.balancecontrol.common.error;

import java.util.List;

/**
 * Single error format returned by the API.
 *
 * @param code    stable identifier the frontend can switch on (e.g. INSUFFICIENT_BALANCE)
 * @param message human-readable text
 * @param details extra information (invalid fields, items without balance...); never null
 */
public record ErrorResponse(String code, String message, List<Object> details) {

    public ErrorResponse {
        details = details == null ? List.of() : List.copyOf(details);
    }

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, List.of());
    }
}

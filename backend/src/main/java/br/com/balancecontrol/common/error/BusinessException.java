package br.com.balancecontrol.common.error;

import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Base class for business exceptions. Each subclass fixes the HTTP status;
 * {@code code} identifies the specific case.
 */
public abstract class BusinessException extends RuntimeException {

    private final String code;
    private final List<Object> details;

    protected BusinessException(String code, String message, List<Object> details) {
        super(message);
        this.code = code;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public abstract HttpStatus getStatus();

    public String getCode() {
        return code;
    }

    public List<Object> getDetails() {
        return details;
    }
}

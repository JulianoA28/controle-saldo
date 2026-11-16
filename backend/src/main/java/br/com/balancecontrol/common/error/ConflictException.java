package br.com.balancecontrol.common.error;

import java.util.List;
import org.springframework.http.HttpStatus;

/** 409 — the operation conflicts with the current state (e.g. order already cancelled, concurrent edit). */
public class ConflictException extends BusinessException {

    public ConflictException(String code, String message) {
        this(code, message, List.of());
    }

    public ConflictException(String code, String message, List<Object> details) {
        super(code, message, details);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}

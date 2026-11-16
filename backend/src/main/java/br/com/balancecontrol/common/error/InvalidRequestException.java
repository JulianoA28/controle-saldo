package br.com.balancecontrol.common.error;

import java.util.List;
import org.springframework.http.HttpStatus;

/** 400 — input that bean validation cannot express (e.g. the same item twice in one order). */
public class InvalidRequestException extends BusinessException {

    public InvalidRequestException(String code, String message) {
        this(code, message, List.of());
    }

    public InvalidRequestException(String code, String message, List<Object> details) {
        super(code, message, details);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}

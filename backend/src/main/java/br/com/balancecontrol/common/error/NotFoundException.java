package br.com.balancecontrol.common.error;

import java.util.List;
import org.springframework.http.HttpStatus;

/** 404 — the requested resource does not exist. */
public class NotFoundException extends BusinessException {

    public NotFoundException(String code, String message) {
        super(code, message, List.of());
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }
}

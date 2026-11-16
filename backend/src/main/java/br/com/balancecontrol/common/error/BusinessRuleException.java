package br.com.balancecontrol.common.error;

import java.util.List;
import org.springframework.http.HttpStatus;

/** 422 — well-formed request that violates a business rule (e.g. insufficient balance). */
public class BusinessRuleException extends BusinessException {

    public BusinessRuleException(String code, String message) {
        this(code, message, List.of());
    }

    public BusinessRuleException(String code, String message, List<Object> details) {
        super(code, message, details);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }
}

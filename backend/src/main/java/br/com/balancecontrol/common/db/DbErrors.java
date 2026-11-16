package br.com.balancecontrol.common.db;

import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;

/** Identifies which named constraint caused a {@link DataIntegrityViolationException}. */
public final class DbErrors {

    private DbErrors() {
    }

    public static boolean isViolationOf(DataIntegrityViolationException ex, String constraintName) {
        String expected = constraintName.toLowerCase(Locale.ROOT);
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof org.hibernate.exception.ConstraintViolationException cve
                    && cve.getConstraintName() != null
                    && cve.getConstraintName().toLowerCase(Locale.ROOT).contains(expected)) {
                return true;
            }
            String message = t.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("\"" + expected + "\"")) {
                return true;
            }
        }
        return false;
    }
}

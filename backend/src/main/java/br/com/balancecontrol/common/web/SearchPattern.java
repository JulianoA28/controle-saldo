package br.com.balancecontrol.common.web;

import java.util.Locale;

/** Builds a case-insensitive LIKE pattern from free text, escaping LIKE wildcards. */
public final class SearchPattern {

    private SearchPattern() {
    }

    /** Blank input matches everything. */
    public static String contains(String text) {
        if (text == null || text.isBlank()) {
            return "%";
        }
        String escaped = text.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}

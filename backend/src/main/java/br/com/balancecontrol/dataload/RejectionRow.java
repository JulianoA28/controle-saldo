package br.com.balancecontrol.dataload;

import com.fasterxml.jackson.databind.JsonNode;

/** A source row that was not loaded. {@code lineNo} is the line in the CSV file (header = 1). */
public record RejectionRow(String source, long lineNo, String sourceKey, String reason, JsonNode raw) {
}

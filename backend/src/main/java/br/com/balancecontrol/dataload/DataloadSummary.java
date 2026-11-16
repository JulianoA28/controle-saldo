package br.com.balancecontrol.dataload;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

public record DataloadSummary(boolean loaded, Instant finishedAt, JsonNode stats, List<ReasonCount> rejections) {

    public record ReasonCount(String source, String reason, long count) {
    }
}

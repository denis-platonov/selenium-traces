package dev.codex.tracing;

import java.util.List;
import java.util.Map;

public record SelenideTraceEvent(
    String type,
    String name,
    String status,
    long startedAtEpochMs,
    long finishedAtEpochMs,
    long durationMs,
    String error,
    Map<String, Object> data,
    List<SelenideTraceAttachment> attachments
) {
}

package dev.codex.tracing;

public record SelenideTraceAttachment(
    String name,
    String file,
    String contentType
) {
}

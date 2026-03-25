package dev.codex.tracing;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SelenideTraceArtifactsIT {
  @TempDir
  Path tempDir;

  @Test
  void verifyCreatesTraceViewerAndArchive() throws Exception {
    SelenideTraceConfig config = SelenideTraceConfig.builder()
        .outputRoot(tempDir)
        .clock(Clock.fixed(Instant.parse("2026-03-23T11:00:00Z"), ZoneOffset.UTC))
        .build();

    SelenideTrace.start("Integration trace", config);
    SelenideTrace.step("manual integration step", () -> {
    });
    SelenideTrace.stopPassed();

    Path sessionDir = tempDir.resolve("Integration_trace-20260323-110000-000");
    Path viewer = sessionDir.resolve("index.html");
    String html = Files.readString(viewer);

    assertTrue(Files.isRegularFile(sessionDir.resolve("trace.trace")));
    assertTrue(Files.isRegularFile(viewer));
    assertTrue(Files.isRegularFile(tempDir.resolve("Integration_trace-20260323-110000-000.zip")));
    assertTrue(html.contains("preview-modes"));
    assertTrue(html.contains("No trace data available") || html.contains("No preview for this event."));
  }
}

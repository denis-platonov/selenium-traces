package dev.codex.tracing;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SelenideTraceLifecycleTest {
  @TempDir
  Path tempDir;

  @Test
  void createsTraceDirectoryAndArchiveWithoutBrowser() throws Exception {
    Clock fixedClock = Clock.fixed(Instant.parse("2026-03-22T18:30:00Z"), ZoneId.of("UTC"));
    SelenideTraceConfig config = SelenideTraceConfig.builder()
        .outputRoot(tempDir)
        .clock(fixedClock)
        .build();

    SelenideTrace.start("Sample test", config);
    SelenideTrace.step("manual step", () -> {
    });
    SelenideTrace.stopPassed();

    Path sessionDir = tempDir.resolve("Sample_test-20260322-183000-000");
    assertTrue(Files.isDirectory(sessionDir));
    assertTrue(Files.isRegularFile(sessionDir.resolve("trace.trace")));
    assertTrue(Files.isRegularFile(sessionDir.resolve("index.html")));
    assertTrue(Files.isRegularFile(tempDir.resolve("Sample_test-20260322-183000-000.zip")));
  }
}

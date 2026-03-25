package dev.codex.tracing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SelenideTraceConfigTest {
  @Test
  void defaultConfigUsesExpectedDefaults() {
    SelenideTraceConfig config = SelenideTraceConfig.defaultConfig();

    assertEquals(Path.of("target", "selenide-traces"), config.outputRoot());
    assertTrue(config.captureScreenshotsOnEveryEvent());
    assertTrue(config.captureDomOnEveryEvent());
    assertTrue(config.captureBrowserLogsOnFinish());
  }

  @Test
  void builderOverridesAllFlags() {
    Clock fixedClock = Clock.fixed(Instant.parse("2026-03-23T00:00:00Z"), ZoneOffset.UTC);
    SelenideTraceConfig config = SelenideTraceConfig.builder()
        .outputRoot(Path.of("custom-output"))
        .captureScreenshotsOnEveryEvent(false)
        .captureDomOnEveryEvent(false)
        .captureBrowserLogsOnFinish(false)
        .clock(fixedClock)
        .build();

    assertEquals(Path.of("custom-output"), config.outputRoot());
    assertFalse(config.captureScreenshotsOnEveryEvent());
    assertFalse(config.captureDomOnEveryEvent());
    assertFalse(config.captureBrowserLogsOnFinish());
    assertSame(fixedClock, config.clock());
  }
}

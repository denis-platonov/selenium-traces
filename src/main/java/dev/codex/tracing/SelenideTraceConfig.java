package dev.codex.tracing;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;

public final class SelenideTraceConfig {
  private final Path outputRoot;
  private final boolean captureScreenshotsOnEveryEvent;
  private final boolean captureDomOnEveryEvent;
  private final boolean captureBrowserLogsOnFinish;
  private final Clock clock;

  private SelenideTraceConfig(Builder builder) {
    this.outputRoot = builder.outputRoot;
    this.captureScreenshotsOnEveryEvent = builder.captureScreenshotsOnEveryEvent;
    this.captureDomOnEveryEvent = builder.captureDomOnEveryEvent;
    this.captureBrowserLogsOnFinish = builder.captureBrowserLogsOnFinish;
    this.clock = builder.clock;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static SelenideTraceConfig defaultConfig() {
    return builder().build();
  }

  public Path outputRoot() {
    return outputRoot;
  }

  public boolean captureScreenshotsOnEveryEvent() {
    return captureScreenshotsOnEveryEvent;
  }

  public boolean captureDomOnEveryEvent() {
    return captureDomOnEveryEvent;
  }

  public boolean captureBrowserLogsOnFinish() {
    return captureBrowserLogsOnFinish;
  }

  public Clock clock() {
    return clock;
  }

  public static final class Builder {
    private Path outputRoot = Path.of("target", "selenide-traces");
    private boolean captureScreenshotsOnEveryEvent = true;
    private boolean captureDomOnEveryEvent = true;
    private boolean captureBrowserLogsOnFinish = true;
    private Clock clock = Clock.systemDefaultZone();

    private Builder() {
    }

    public Builder outputRoot(Path outputRoot) {
      this.outputRoot = Objects.requireNonNull(outputRoot, "outputRoot");
      return this;
    }

    public Builder captureScreenshotsOnEveryEvent(boolean enabled) {
      this.captureScreenshotsOnEveryEvent = enabled;
      return this;
    }

    public Builder captureDomOnEveryEvent(boolean enabled) {
      this.captureDomOnEveryEvent = enabled;
      return this;
    }

    public Builder captureBrowserLogsOnFinish(boolean enabled) {
      this.captureBrowserLogsOnFinish = enabled;
      return this;
    }

    public Builder clock(Clock clock) {
      this.clock = Objects.requireNonNull(clock, "clock");
      return this;
    }

    public SelenideTraceConfig build() {
      return new SelenideTraceConfig(this);
    }
  }
}

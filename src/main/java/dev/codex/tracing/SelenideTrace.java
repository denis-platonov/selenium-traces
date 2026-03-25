package dev.codex.tracing;

import com.codeborne.selenide.logevents.LogEvent;
import com.codeborne.selenide.logevents.LogEventListener;
import com.codeborne.selenide.logevents.SelenideLogger;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import org.openqa.selenium.WebDriver;

public final class SelenideTrace {
  private static final String LISTENER_NAME = "codex-selenide-trace-listener";
  private static final ThreadLocal<SelenideTraceSession> CURRENT_SESSION = new ThreadLocal<>();
  private static final LogEventListener LISTENER = new SelenideLogEventBridge();

  private SelenideTrace() {
  }

  public static void start(String testName) {
    start(testName, SelenideTraceConfig.defaultConfig());
  }

  public static void start(String testName, SelenideTraceConfig config) {
    startInternal(testName, null, config);
  }

  static void startForDriver(String testName, WebDriver driver, SelenideTraceConfig config) {
    startInternal(testName, driver, config);
  }

  private static void startInternal(String testName, WebDriver driver, SelenideTraceConfig config) {
    Objects.requireNonNull(testName, "testName");
    Objects.requireNonNull(config, "config");

    String normalized = normalizeFileName(testName);
    String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS", Locale.ROOT)
        .format(config.clock().instant().atZone(config.clock().getZone()));
    Path sessionDirectory = config.outputRoot().resolve(normalized + "-" + timestamp);

    SelenideTraceSession session = new SelenideTraceSession(testName, config, sessionDirectory, driver);
    session.start();
    CURRENT_SESSION.set(session);

    SelenideLogger.removeListener(LISTENER_NAME);
    SelenideLogger.addListener(LISTENER_NAME, LISTENER);
  }

  public static void stopPassed() {
    stop("passed", null);
  }

  public static void stopFailed(Throwable error) {
    stop("failed", error);
  }

  public static void stop(String status, Throwable error) {
    SelenideLogger.removeListener(LISTENER_NAME);
    SelenideTraceSession session = CURRENT_SESSION.get();
    CURRENT_SESSION.remove();
    if (session != null) {
      session.finish(status, error);
    }
  }

  public static void step(String name, Runnable runnable) {
    SelenideTraceSession session = CURRENT_SESSION.get();
    if (session == null) {
      runnable.run();
      return;
    }

    SelenideTraceSession.TraceStep step = session.pushManualStep(name);
    Throwable failure = null;
    try {
      runnable.run();
    } catch (Throwable throwable) {
      failure = throwable;
      throw throwable;
    } finally {
      session.completeManualStep(step, failure);
    }
  }

  static SelenideTraceSession currentSession() {
    return CURRENT_SESSION.get();
  }

  private static String normalizeFileName(String value) {
    return value.replaceAll("[^a-zA-Z0-9._-]+", "_");
  }

  private static final class SelenideLogEventBridge implements LogEventListener {
    @Override
    public void beforeEvent(LogEvent currentLog) {
      SelenideTraceSession session = CURRENT_SESSION.get();
      if (session == null) {
        return;
      }
      session.captureActionTarget(
          stringValue(currentLog, "getSubject"),
          stringValue(currentLog, "getElement"));
    }

    @Override
    public void afterEvent(LogEvent currentLog) {
      SelenideTraceSession session = CURRENT_SESSION.get();
      if (session == null) {
        return;
      }

      session.recordSelenideStep(
          describe(currentLog),
          stringValue(currentLog, "getSubject"),
          stringValue(currentLog, "getElement"),
          throwable(currentLog),
          longValue(currentLog, "getDuration"));
    }

    private static String describe(LogEvent currentLog) {
      String subject = stringValue(currentLog, "getSubject");
      String element = stringValue(currentLog, "getElement");
      if (subject == null && element == null) {
        return "selenide-action";
      }
      if (subject == null) {
        return element;
      }
      if (element == null || element.isBlank()) {
        return subject;
      }
      return subject + " " + element;
    }

    private static Throwable throwable(LogEvent currentLog) {
      try {
        Object value = currentLog.getClass().getMethod("getError").invoke(currentLog);
        return value instanceof Throwable throwable ? throwable : null;
      } catch (ReflectiveOperationException ignored) {
        return null;
      }
    }

    private static Long longValue(LogEvent currentLog, String methodName) {
      try {
        Object value = currentLog.getClass().getMethod(methodName).invoke(currentLog);
        return value instanceof Number number ? number.longValue() : null;
      } catch (ReflectiveOperationException ignored) {
        return null;
      }
    }

    private static String stringValue(LogEvent currentLog, String methodName) {
      try {
        Object value = currentLog.getClass().getMethod(methodName).invoke(currentLog);
        return value == null ? null : value.toString();
      } catch (ReflectiveOperationException ignored) {
        return null;
      }
    }
  }
}

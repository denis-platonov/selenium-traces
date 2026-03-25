package dev.codex.tracing;

import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

public final class SeleniumTrace {
  private SeleniumTrace() {
  }

  public static void start(String testName, WebDriver driver) {
    start(testName, driver, SelenideTraceConfig.defaultConfig());
  }

  public static void start(String testName, WebDriver driver, SelenideTraceConfig config) {
    SelenideTrace.startForDriver(testName, driver, config);
  }

  public static void stopPassed() {
    SelenideTrace.stopPassed();
  }

  public static void stopFailed(Throwable error) {
    SelenideTrace.stopFailed(error);
  }

  public static void stop(String status, Throwable error) {
    SelenideTrace.stop(status, error);
  }

  public static void step(String name, Runnable runnable) {
    SelenideTrace.step(name, runnable);
  }

  public static void action(String name, WebDriver driver, Object target, TraceRunnable runnable) {
    Objects.requireNonNull(driver, "driver");
    Objects.requireNonNull(runnable, "runnable");

    SelenideTraceSession session = SelenideTrace.currentSession();
    if (session == null) {
      runUnchecked(runnable);
      return;
    }

    long startedAt = System.nanoTime();
    Throwable failure = null;
    session.captureActionTarget(driver, target);
    try {
      runnable.run();
    } catch (Throwable throwable) {
      failure = throwable;
      throwUnchecked(throwable);
    } finally {
      long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
      session.recordExternalStep(name, "selenium", describeTarget(target), failure, durationMs, driver);
    }
  }

  public static <T> T query(String name, WebDriver driver, Object target, TraceSupplier<T> supplier) {
    Objects.requireNonNull(driver, "driver");
    Objects.requireNonNull(supplier, "supplier");

    SelenideTraceSession session = SelenideTrace.currentSession();
    if (session == null) {
      return getUnchecked(supplier);
    }

    long startedAt = System.nanoTime();
    Throwable failure = null;
    session.captureActionTarget(driver, target);
    try {
      return supplier.get();
    } catch (Throwable throwable) {
      failure = throwable;
      throwUnchecked(throwable);
      return null;
    } finally {
      long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
      session.recordExternalStep(name, "selenium", describeTarget(target), failure, durationMs, driver);
    }
  }

  private static String describeTarget(Object target) {
    if (target == null) {
      return null;
    }
    if (target instanceof By by) {
      return by.toString();
    }
    if (target instanceof Collection<?> collection) {
      return "collection[" + collection.size() + "]";
    }
    if (target instanceof WebElement element) {
      return element.toString();
    }
    return String.valueOf(target);
  }

  private static void runUnchecked(TraceRunnable runnable) {
    try {
      runnable.run();
    } catch (Throwable throwable) {
      throwUnchecked(throwable);
    }
  }

  private static <T> T getUnchecked(TraceSupplier<T> supplier) {
    try {
      return supplier.get();
    } catch (Throwable throwable) {
      throwUnchecked(throwable);
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void throwUnchecked(Throwable throwable) throws E {
    throw (E) throwable;
  }

  @FunctionalInterface
  public interface TraceRunnable {
    void run() throws Exception;
  }

  @FunctionalInterface
  public interface TraceSupplier<T> {
    T get() throws Exception;
  }
}

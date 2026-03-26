package dev.codex.tracing;

import java.util.Collection;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

final class SeleniumTraceSupport {
  private SeleniumTraceSupport() {
  }

  @FunctionalInterface
  interface TraceRunnable {
    void run() throws Exception;
  }

  @FunctionalInterface
  interface TraceSupplier<T> {
    T get() throws Exception;
  }

  static void start(String testName, WebDriver driver) {
    start(testName, driver, SelenideTraceConfig.defaultConfig());
  }

  static void start(String testName, WebDriver driver, SelenideTraceConfig config) {
    SelenideTrace.startForDriver(testName, driver, config);
  }

  static void stopPassed() {
    SelenideTrace.stopPassed();
  }

  static void stopFailed(Throwable error) {
    SelenideTrace.stopFailed(error);
  }

  static void stop(String status, Throwable error) {
    SelenideTrace.stop(status, error);
  }

  static void step(String name, Runnable runnable) {
    SelenideTrace.step(name, runnable);
  }

  static void action(String name, WebDriver driver, Object target, TraceRunnable runnable) {
    trace(name, driver, target, () -> {
      runnable.run();
      return null;
    });
  }

  static <T> T query(String name, WebDriver driver, Object target, TraceSupplier<T> supplier) {
    return trace(name, driver, target, supplier::get);
  }

  static <T> T trace(String name, WebDriver driver, Object target, ThrowingSupplier<T> supplier) {
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
      Throwable recordedFailure = failure;
      if (driver instanceof TracingSelenideChromeDriver tracingDriver) {
        tracingDriver.withoutTracing(() -> {
          session.recordExternalStep(name, "selenium", describeTarget(target), recordedFailure, durationMs, driver);
          return null;
        });
      } else {
        session.recordExternalStep(name, "selenium", describeTarget(target), recordedFailure, durationMs, driver);
      }
    }
  }

  static String describeTarget(Object target) {
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

  private static <T> T getUnchecked(ThrowingSupplier<T> supplier) {
    try {
      return supplier.get();
    } catch (Throwable throwable) {
      throwUnchecked(throwable);
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  static <E extends Throwable> void throwUnchecked(Throwable throwable) throws E {
    throw (E) throwable;
  }

  @FunctionalInterface
  interface ThrowingSupplier<T> {
    T get() throws Exception;
  }
}

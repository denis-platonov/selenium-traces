package dev.codex.tracing;

import org.junit.jupiter.api.AfterEach;

abstract class AbstractSeleniumSystemTest {
  protected TracingSelenideChromeDriver driver;

  @AfterEach
  void tearDownDriver() {
    if (driver != null) {
      driver.quit();
    }
  }

  protected TracingSelenideChromeDriver startChrome(String testName) {
    driver = new TracingSelenideChromeDriver(testName, BrowserTestSupport.chromeOptions());
    return driver;
  }

  protected void runTraced(ThrowingRunnable runnable) throws Exception {
    try {
      runnable.run();
    } catch (Throwable throwable) {
      driver.stopTraceFailed(throwable);
      throw throwable;
    }
  }

  @FunctionalInterface
  protected interface ThrowingRunnable {
    void run() throws Exception;
  }
}

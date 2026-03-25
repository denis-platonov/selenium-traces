package dev.codex.tracing;

import org.junit.jupiter.api.AfterEach;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

abstract class AbstractSeleniumSystemTest {
  protected WebDriver driver;

  @AfterEach
  void tearDownDriver() {
    if (driver != null) {
      driver.quit();
    }
  }

  protected WebDriver startChrome() {
    driver = new ChromeDriver(BrowserTestSupport.chromeOptions());
    return driver;
  }

  protected void runTraced(String testName, ThrowingRunnable runnable) throws Exception {
    SeleniumTrace.start(testName, driver);
    Throwable failure = null;
    try {
      runnable.run();
    } catch (Throwable throwable) {
      failure = throwable;
      SeleniumTrace.stopFailed(throwable);
      throw throwable;
    } finally {
      if (failure == null && SelenideTrace.currentSession() != null) {
        SeleniumTrace.stopPassed();
      }
    }
  }

  @FunctionalInterface
  protected interface ThrowingRunnable {
    void run() throws Exception;
  }
}

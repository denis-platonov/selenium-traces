package dev.codex.tracing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

class DemoFailingBrowserTraceST {
  private TracingSelenideChromeDriver driver;

  @AfterEach
  void tearDown() {
    if (driver != null) {
      driver.quit();
    }
  }

  @Test
  @Disabled("Intentional report demo case; excluded from the default passing suite.")
  void intentionallyFailsWithTraceForReportDemo() {
    driver = new TracingSelenideChromeDriver(
        "DemoFailingBrowserTraceST.intentionallyFailsWithTraceForReportDemo",
        BrowserTestSupport.chromeOptions());
    try {
      driver.get("https://the-internet.herokuapp.com/windows");

      WebElement heading = driver.findElement(By.tagName("h3"));
      assertEquals("This heading is intentionally wrong", heading.getText());
    } catch (Throwable throwable) {
      driver.stopTraceFailed(throwable);
      throw throwable;
    }
  }
}

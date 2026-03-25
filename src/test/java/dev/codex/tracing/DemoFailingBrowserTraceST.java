package dev.codex.tracing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

class DemoFailingBrowserTraceST {
  private WebDriver driver;

  @AfterEach
  void tearDown() {
    if (driver != null) {
      driver.quit();
    }
  }

  @Test
  @Disabled("Intentional report demo case; excluded from the default passing suite.")
  void intentionallyFailsWithTraceForReportDemo() {
    ChromeOptions options = new ChromeOptions();
    options.addArguments("--headless=new", "--window-size=1440,900", "--disable-dev-shm-usage", "--no-sandbox");
    driver = new ChromeDriver(options);

    SeleniumTrace.start("DemoFailingBrowserTraceST.intentionallyFailsWithTraceForReportDemo", driver);
    try {
      SeleniumTrace.step("Open page", () -> driver.get("https://the-internet.herokuapp.com/windows"));

      WebElement heading = SeleniumTrace.query("Find heading", driver, By.tagName("h3"),
          () -> driver.findElement(By.tagName("h3")));

      SeleniumTrace.action("Assert intentionally wrong heading", driver, heading,
          () -> assertEquals("This heading is intentionally wrong", heading.getText()));

      SeleniumTrace.stopPassed();
    } catch (Throwable throwable) {
      SeleniumTrace.stopFailed(throwable);
      throw throwable;
    }
  }
}

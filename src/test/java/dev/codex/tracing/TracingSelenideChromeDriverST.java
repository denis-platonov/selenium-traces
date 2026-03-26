package dev.codex.tracing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

class TracingSelenideChromeDriverST {
  private TracingSelenideChromeDriver driver;

  @AfterEach
  void tearDown() {
    if (driver != null) {
      driver.quit();
    }
  }

  @Test
  void capturesTraceForPlainSeleniumFlow() {
    driver = new TracingSelenideChromeDriver(
        "TracingSelenideChromeDriverST.capturesTraceForPlainSeleniumFlow",
        BrowserTestSupport.chromeOptions());
    try {
      driver.get("https://the-internet.herokuapp.com/windows");

      WebElement heading = driver.findElement(By.tagName("h3"));
      assertEquals("Opening a new window", heading.getText());

      WebElement clickHere = driver.findElement(By.cssSelector("a[href='/windows/new']"));
      clickHere.click();

      new WebDriverWait(driver, Duration.ofSeconds(10))
          .until(d -> d.getWindowHandles().size() > 1);
      driver.switchTo().window(driver.getWindowHandles().stream().skip(1).findFirst().orElseThrow());

      WebElement newWindowHeading = driver.findElement(By.tagName("h3"));
      assertEquals("New Window", newWindowHeading.getText());
      assertTrue(driver.getCurrentUrl().endsWith("/windows/new"));
    } catch (Throwable throwable) {
      driver.stopTraceFailed(throwable);
      throw throwable;
    }
  }
}

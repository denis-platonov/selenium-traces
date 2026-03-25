package dev.codex.tracing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

class SeleniumTraceST {
  private WebDriver driver;

  @AfterEach
  void tearDown() {
    if (driver != null) {
      driver.quit();
    }
  }

  @Test
  void capturesTraceForPlainSeleniumFlow() {
    ChromeOptions options = new ChromeOptions();
    options.addArguments("--headless=new", "--window-size=1440,900", "--disable-dev-shm-usage", "--no-sandbox");
    driver = new ChromeDriver(options);

    SeleniumTrace.start("SeleniumTraceST.capturesTraceForPlainSeleniumFlow", driver);
    Throwable failure = null;
    try {
      SeleniumTrace.step("Open page", () -> driver.get("https://the-internet.herokuapp.com/windows"));

      WebElement heading = SeleniumTrace.query("Find heading", driver, By.tagName("h3"),
          () -> driver.findElement(By.tagName("h3")));
      SeleniumTrace.action("Assert heading text", driver, heading,
          () -> assertEquals("Opening a new window", heading.getText()));

      WebElement clickHere = SeleniumTrace.query("Find link", driver, By.cssSelector("a[href='/windows/new']"),
          () -> driver.findElement(By.cssSelector("a[href='/windows/new']")));
      SeleniumTrace.action("Click link", driver, clickHere, clickHere::click);

      SeleniumTrace.step("Switch to new window", () -> {
        new WebDriverWait(driver, Duration.ofSeconds(10))
            .until(d -> d.getWindowHandles().size() > 1);
        driver.switchTo().window(driver.getWindowHandles().stream().skip(1).findFirst().orElseThrow());
      });

      WebElement newWindowHeading = SeleniumTrace.query("Find new window heading", driver, By.tagName("h3"),
          () -> driver.findElement(By.tagName("h3")));
      SeleniumTrace.action("Assert new window text", driver, newWindowHeading,
          () -> assertEquals("New Window", newWindowHeading.getText()));
      SeleniumTrace.action("Assert destination url", driver, By.tagName("body"),
          () -> assertTrue(driver.getCurrentUrl().endsWith("/windows/new")));

      SeleniumTrace.stopPassed();
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
}

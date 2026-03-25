package dev.codex.tracing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

class SeleniumWaitSuiteST extends AbstractSeleniumSystemTest {
  @Test
  void waitsForVisibilityAndReadsText() throws Exception {
    WebDriver driver = startChrome();

    runTraced("SeleniumWaitSuiteST.waitsForVisibilityAndReadsText", () -> {
      SeleniumTrace.step("Open coverage page", () -> driver.get(BrowserTestSupport.traceCoveragePageUrl()));

      WebElement visibleElement = SeleniumTrace.query(
          "Wait for visible element",
          driver,
          By.id("delayedVisible"),
          () -> new WebDriverWait(driver, Duration.ofSeconds(5))
              .until(ExpectedConditions.visibilityOfElementLocated(By.id("delayedVisible"))));

      SeleniumTrace.action("Assert visible text", driver, visibleElement,
          () -> assertEquals("Now visible", visibleElement.getText()));
    });
  }

  @Test
  void waitsForEnabledAndClicks() throws Exception {
    WebDriver driver = startChrome();

    runTraced("SeleniumWaitSuiteST.waitsForEnabledAndClicks", () -> {
      SeleniumTrace.step("Open coverage page", () -> driver.get(BrowserTestSupport.traceCoveragePageUrl()));

      WebElement enabledButton = SeleniumTrace.query(
          "Wait for enabled button",
          driver,
          By.id("delayedEnable"),
          () -> new WebDriverWait(driver, Duration.ofSeconds(5))
              .until(ExpectedConditions.elementToBeClickable(By.id("delayedEnable"))));

      SeleniumTrace.action("Click enabled button", driver, enabledButton, enabledButton::click);

      WebElement buttonStatus = SeleniumTrace.query(
          "Find button status",
          driver,
          By.id("buttonStatus"),
          () -> driver.findElement(By.id("buttonStatus")));
      SeleniumTrace.action("Assert button status", driver, buttonStatus,
          () -> assertEquals("Enabled button clicked", buttonStatus.getText()));
    });
  }

  @Test
  void waitsForPresenceAndReadsAttribute() throws Exception {
    WebDriver driver = startChrome();

    runTraced("SeleniumWaitSuiteST.waitsForPresenceAndReadsAttribute", () -> {
      SeleniumTrace.step("Open coverage page", () -> driver.get(BrowserTestSupport.traceCoveragePageUrl()));

      WebElement lateElement = SeleniumTrace.query(
          "Wait for present element",
          driver,
          By.id("lateElement"),
          () -> new WebDriverWait(driver, Duration.ofSeconds(5))
              .until(ExpectedConditions.presenceOfElementLocated(By.id("lateElement"))));

      SeleniumTrace.action("Assert late element attribute", driver, lateElement,
          () -> assertEquals("late", lateElement.getAttribute("data-role")));
    });
  }
}

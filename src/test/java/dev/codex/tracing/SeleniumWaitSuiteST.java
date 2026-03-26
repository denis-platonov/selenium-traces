package dev.codex.tracing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

class SeleniumWaitSuiteST extends AbstractSeleniumSystemTest {
  @Test
  void waitsForVisibilityAndReadsText() throws Exception {
    TracingSelenideChromeDriver driver = startChrome("SeleniumWaitSuiteST.waitsForVisibilityAndReadsText");

    runTraced(() -> {
      driver.get(BrowserTestSupport.traceCoveragePageUrl());

      WebElement visibleElement = new WebDriverWait(driver, Duration.ofSeconds(5))
          .until(ExpectedConditions.visibilityOfElementLocated(By.id("delayedVisible")));
      assertEquals("Now visible", visibleElement.getText());
    });
  }

  @Test
  void waitsForEnabledAndClicks() throws Exception {
    TracingSelenideChromeDriver driver = startChrome("SeleniumWaitSuiteST.waitsForEnabledAndClicks");

    runTraced(() -> {
      driver.get(BrowserTestSupport.traceCoveragePageUrl());

      WebElement enabledButton = new WebDriverWait(driver, Duration.ofSeconds(5))
          .until(ExpectedConditions.elementToBeClickable(By.id("delayedEnable")));

      enabledButton.click();

      WebElement buttonStatus = driver.findElement(By.id("buttonStatus"));
      assertEquals("Enabled button clicked", buttonStatus.getText());
    });
  }

  @Test
  void waitsForPresenceAndReadsAttribute() throws Exception {
    TracingSelenideChromeDriver driver = startChrome("SeleniumWaitSuiteST.waitsForPresenceAndReadsAttribute");

    runTraced(() -> {
      driver.get(BrowserTestSupport.traceCoveragePageUrl());

      WebElement lateElement = new WebDriverWait(driver, Duration.ofSeconds(5))
          .until(ExpectedConditions.presenceOfElementLocated(By.id("lateElement")));
      assertEquals("late", lateElement.getAttribute("data-role"));
    });
  }
}

package dev.codex.tracing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

class SeleniumInteractionSuiteST extends AbstractSeleniumSystemTest {
  @Test
  void findsElementsTypesAndClicks() throws Exception {
    TracingSelenideChromeDriver driver = startChrome("SeleniumInteractionSuiteST.findsElementsTypesAndClicks");

    runTraced(() -> {
      driver.get(BrowserTestSupport.traceCoveragePageUrl());

      WebElement input = driver.findElement(By.id("nameInput"));
      input.sendKeys("Codex");

      new WebDriverWait(driver, Duration.ofSeconds(5))
          .until(ExpectedConditions.textToBePresentInElementLocated(By.id("typedValue"), "Codex"));
      WebElement typedValue = driver.findElement(By.id("typedValue"));
      assertEquals("Codex", typedValue.getText());

      WebElement clickTarget = driver.findElement(By.id("clickTarget"));
      clickTarget.click();

      WebElement clickStatus = driver.findElement(By.id("clickStatus"));
      assertEquals("Clicked", clickStatus.getText());
    });
  }

  @Test
  void hoversFindsCollectionsAndReadsAttributes() throws Exception {
    TracingSelenideChromeDriver driver = startChrome("SeleniumInteractionSuiteST.hoversFindsCollectionsAndReadsAttributes");

    runTraced(() -> {
      driver.get(BrowserTestSupport.traceCoveragePageUrl());

      WebElement hoverTarget = driver.findElement(By.id("hoverTarget"));
      new Actions(driver).moveToElement(hoverTarget).perform();

      new WebDriverWait(driver, Duration.ofSeconds(5))
          .until(ExpectedConditions.textToBePresentInElementLocated(By.id("hoverStatus"), "Hovered"));
      WebElement hoverStatus = driver.findElement(By.id("hoverStatus"));
      assertEquals("Hovered", hoverStatus.getText());

      List<WebElement> items = driver.findElements(By.cssSelector(".item"));
      assertEquals(3, items.size());

      WebElement beta = driver.findElement(By.xpath("//li[normalize-space()='Beta']"));
      assertEquals("secondary", beta.getAttribute("data-kind"));
      assertEquals("Beta", beta.getText());
    });
  }
}

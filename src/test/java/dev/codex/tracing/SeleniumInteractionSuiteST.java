package dev.codex.tracing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

class SeleniumInteractionSuiteST extends AbstractSeleniumSystemTest {
  @Test
  void findsElementsTypesAndClicks() throws Exception {
    WebDriver driver = startChrome();

    runTraced("SeleniumInteractionSuiteST.findsElementsTypesAndClicks", () -> {
      SeleniumTrace.step("Open coverage page", () -> driver.get(BrowserTestSupport.traceCoveragePageUrl()));

      WebElement input = SeleniumTrace.query("Find name input", driver, By.id("nameInput"),
          () -> driver.findElement(By.id("nameInput")));
      SeleniumTrace.action("Type into input", driver, input, () -> input.sendKeys("Codex"));

      WebElement typedValue = SeleniumTrace.query("Find typed value", driver, By.id("typedValue"),
          () -> {
            new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(ExpectedConditions.textToBePresentInElementLocated(By.id("typedValue"), "Codex"));
            return driver.findElement(By.id("typedValue"));
          });
      SeleniumTrace.action("Assert typed value", driver, typedValue,
          () -> assertEquals("Codex", typedValue.getText()));

      WebElement clickTarget = SeleniumTrace.query("Find click target", driver, By.id("clickTarget"),
          () -> driver.findElement(By.id("clickTarget")));
      SeleniumTrace.action("Click target", driver, clickTarget, clickTarget::click);

      WebElement clickStatus = SeleniumTrace.query("Find click status", driver, By.id("clickStatus"),
          () -> driver.findElement(By.id("clickStatus")));
      SeleniumTrace.action("Assert click status", driver, clickStatus,
          () -> assertEquals("Clicked", clickStatus.getText()));
    });
  }

  @Test
  void hoversFindsCollectionsAndReadsAttributes() throws Exception {
    WebDriver driver = startChrome();

    runTraced("SeleniumInteractionSuiteST.hoversFindsCollectionsAndReadsAttributes", () -> {
      SeleniumTrace.step("Open coverage page", () -> driver.get(BrowserTestSupport.traceCoveragePageUrl()));

      WebElement hoverTarget = SeleniumTrace.query("Find hover target", driver, By.id("hoverTarget"),
          () -> driver.findElement(By.id("hoverTarget")));
      SeleniumTrace.action("Hover target", driver, hoverTarget,
          () -> new Actions(driver).moveToElement(hoverTarget).perform());

      WebElement hoverStatus = SeleniumTrace.query("Find hover status", driver, By.id("hoverStatus"),
          () -> {
            new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(ExpectedConditions.textToBePresentInElementLocated(By.id("hoverStatus"), "Hovered"));
            return driver.findElement(By.id("hoverStatus"));
          });
      SeleniumTrace.action("Assert hover status", driver, hoverStatus,
          () -> assertEquals("Hovered", hoverStatus.getText()));

      List<WebElement> items = SeleniumTrace.query("Find item collection", driver, By.cssSelector(".item"),
          () -> driver.findElements(By.cssSelector(".item")));
      SeleniumTrace.action("Assert item collection size", driver, items,
          () -> assertEquals(3, items.size()));

      WebElement beta = SeleniumTrace.query("Find Beta item", driver, By.xpath("//li[normalize-space()='Beta']"),
          () -> driver.findElement(By.xpath("//li[normalize-space()='Beta']")));
      SeleniumTrace.action("Assert Beta attribute", driver, beta,
          () -> assertEquals("secondary", beta.getAttribute("data-kind")));
      SeleniumTrace.action("Assert Beta text", driver, beta,
          () -> assertEquals("Beta", beta.getText()));
    });
  }
}

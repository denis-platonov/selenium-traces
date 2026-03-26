package dev.codex.tracing;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public final class ReadmeScreenshotGenerator {
  private ReadmeScreenshotGenerator() {
  }

  public static void main(String[] args) throws Exception {
    Path root = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
    Path screenshots = root.resolve("docs").resolve("screenshots");
    Files.createDirectories(screenshots);

    Path report = root.resolve("target").resolve("test-report").resolve("index.html");
    Path latestTrace = Files.list(root.resolve("target").resolve("selenide-traces"))
        .filter(Files::isDirectory)
        .max(Comparator.comparing(ReadmeScreenshotGenerator::lastModified))
        .orElseThrow(() -> new IllegalStateException("No trace directories found"));

    ChromeOptions options = BrowserTestSupport.chromeOptions();
    WebDriver driver = new ChromeDriver(options);
    try {
      capture(driver, report.toUri().toString(), screenshots.resolve("test-report-home.png"), List.of());
      capture(driver, report.toUri().toString(), screenshots.resolve("test-report-suites.png"), List.of(
          () -> expandAllSuites(driver),
          () -> scrollToTop(driver)));
      capture(driver, latestTrace.resolve("index.html").toUri().toString(), screenshots.resolve("trace-viewer-overview.png"), List.of());
      capture(driver, latestTrace.resolve("index.html").toUri().toString(), screenshots.resolve("trace-viewer-network.png"), List.of(
          () -> driver.findElement(By.cssSelector(".tab[data-tab='network']")).click(),
          () -> new WebDriverWait(driver, Duration.ofSeconds(5))
              .until(ExpectedConditions.visibilityOfElementLocated(By.id("network-list")))));
    } finally {
      driver.quit();
    }
  }

  private static void capture(WebDriver driver, String url, Path output, List<ThrowingRunnable> steps) throws Exception {
    driver.get(url);
    new WebDriverWait(driver, Duration.ofSeconds(10))
        .until(webDriver -> ((TakesScreenshot) webDriver).getScreenshotAs(OutputType.BYTES).length > 0);
    for (ThrowingRunnable step : steps) {
      step.run();
    }
    WebElement body = driver.findElement(By.tagName("body"));
    new WebDriverWait(driver, Duration.ofSeconds(10)).until(ExpectedConditions.visibilityOf(body));
    Files.write(output, ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES));
  }

  private static void expandAllSuites(WebDriver driver) {
    ((JavascriptExecutor) driver).executeScript("""
        document.querySelectorAll('.suite-item').forEach(item => item.open = true);
        """);
  }

  private static void scrollToTop(WebDriver driver) {
    ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");
  }

  private static java.time.Instant lastModified(Path path) {
    try {
      return Files.getLastModifiedTime(path).toInstant();
    } catch (Exception exception) {
      return java.time.Instant.EPOCH;
    }
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }
}

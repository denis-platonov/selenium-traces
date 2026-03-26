package dev.codex.tracing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WindowType;
import org.openqa.selenium.WrapsDriver;
import org.openqa.selenium.WrapsElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.Logs;

public class TracingSelenideChromeDriver extends ChromeDriver {
  private final String traceName;
  private final SelenideTraceConfig traceConfig;
  private final ThreadLocal<Integer> tracingSuppressionDepth = ThreadLocal.withInitial(() -> 0);
  private boolean traceStopped;

  public TracingSelenideChromeDriver() {
    this(inferTestName(), new ChromeOptions(), SelenideTraceConfig.defaultConfig());
  }

  public TracingSelenideChromeDriver(ChromeOptions options) {
    this(inferTestName(), options, SelenideTraceConfig.defaultConfig());
  }

  public TracingSelenideChromeDriver(String testName) {
    this(testName, new ChromeOptions(), SelenideTraceConfig.defaultConfig());
  }

  public TracingSelenideChromeDriver(String testName, ChromeOptions options) {
    this(testName, options, SelenideTraceConfig.defaultConfig());
  }

  public TracingSelenideChromeDriver(String testName, ChromeOptions options, SelenideTraceConfig config) {
    super(options);
    this.traceName = Objects.requireNonNull(testName, "testName");
    this.traceConfig = Objects.requireNonNull(config, "config");
    SeleniumTraceSupport.start(traceName, this, traceConfig);
  }

  public TracingSelenideChromeDriver(ChromeDriverService service, ChromeOptions options) {
    this(inferTestName(), service, options, SelenideTraceConfig.defaultConfig());
  }

  public TracingSelenideChromeDriver(String testName, ChromeDriverService service, ChromeOptions options) {
    this(testName, service, options, SelenideTraceConfig.defaultConfig());
  }

  public TracingSelenideChromeDriver(String testName, ChromeDriverService service, ChromeOptions options, SelenideTraceConfig config) {
    super(service, options);
    this.traceName = Objects.requireNonNull(testName, "testName");
    this.traceConfig = Objects.requireNonNull(config, "config");
    SeleniumTraceSupport.start(traceName, this, traceConfig);
  }

  public void stopTracePassed() {
    if (traceStopped || SelenideTrace.currentSession() == null) {
      traceStopped = true;
      return;
    }
    traceStopped = true;
    withoutTracing(() -> {
      SeleniumTraceSupport.stopPassed();
      return null;
    });
  }

  public void stopTraceFailed(Throwable error) {
    if (traceStopped || SelenideTrace.currentSession() == null) {
      traceStopped = true;
      return;
    }
    traceStopped = true;
    withoutTracing(() -> {
      SeleniumTraceSupport.stopFailed(error);
      return null;
    });
  }

  @Override
  public void get(String url) {
    traceAction("Open page", url, () -> super.get(url));
  }

  @Override
  public String getCurrentUrl() {
    return traceQuery("Read current url", By.tagName("body"), super::getCurrentUrl);
  }

  @Override
  public String getTitle() {
    return traceQuery("Read title", By.tagName("title"), super::getTitle);
  }

  @Override
  public List<WebElement> findElements(By by) {
    return traceQuery("Find elements", by, () -> wrapElements(super.findElements(by), by.toString()));
  }

  @Override
  public WebElement findElement(By by) {
    return traceQuery("Find element", by, () -> wrapElement(super.findElement(by), by.toString()));
  }

  @Override
  public String getPageSource() {
    return traceQuery("Read page source", By.tagName("body"), super::getPageSource);
  }

  @Override
  public void close() {
    try {
      stopTracePassed();
    } finally {
      super.close();
    }
  }

  @Override
  public void quit() {
    try {
      stopTracePassed();
    } finally {
      super.quit();
    }
  }

  @Override
  public Set<String> getWindowHandles() {
    return traceQuery("Read window handles", By.tagName("body"), super::getWindowHandles);
  }

  @Override
  public String getWindowHandle() {
    return traceQuery("Read active window handle", By.tagName("body"), super::getWindowHandle);
  }

  @Override
  public TargetLocator switchTo() {
    return new TracingTargetLocator(super.switchTo());
  }

  @Override
  public Navigation navigate() {
    return new TracingNavigation(super.navigate());
  }

  @Override
  public Options manage() {
    return new TracingOptions(super.manage());
  }

  private void traceAction(String name, Object target, SeleniumTraceSupport.TraceRunnable runnable) {
    if (isTracingSuppressed()) {
      try {
        runnable.run();
        return;
      } catch (Throwable throwable) {
        SeleniumTraceSupport.throwUnchecked(throwable);
      }
    }
    SeleniumTraceSupport.action(name, this, target, runnable);
  }

  private <T> T traceQuery(String name, Object target, SeleniumTraceSupport.TraceSupplier<T> supplier) {
    if (isTracingSuppressed()) {
      try {
        return supplier.get();
      } catch (Throwable throwable) {
        SeleniumTraceSupport.throwUnchecked(throwable);
        return null;
      }
    }
    return SeleniumTraceSupport.query(name, this, target, supplier);
  }

  <T> T withoutTracing(SeleniumTraceSupport.ThrowingSupplier<T> supplier) {
    tracingSuppressionDepth.set(tracingSuppressionDepth.get() + 1);
    try {
      return supplier.get();
    } catch (Throwable throwable) {
      SeleniumTraceSupport.throwUnchecked(throwable);
      return null;
    } finally {
      int nextDepth = tracingSuppressionDepth.get() - 1;
      if (nextDepth <= 0) {
        tracingSuppressionDepth.remove();
      } else {
        tracingSuppressionDepth.set(nextDepth);
      }
    }
  }

  private boolean isTracingSuppressed() {
    return tracingSuppressionDepth.get() > 0;
  }

  private WebElement wrapElement(WebElement element, String descriptor) {
    if (element == null) {
      return null;
    }
    return new TracingWebElement(element, descriptor);
  }

  private List<WebElement> wrapElements(List<WebElement> elements, String descriptor) {
    List<WebElement> wrapped = new ArrayList<>(elements.size());
    for (WebElement element : elements) {
      wrapped.add(wrapElement(element, descriptor));
    }
    return wrapped;
  }

  private static String inferTestName() {
    for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
      String className = frame.getClassName();
      if (className.equals(TracingSelenideChromeDriver.class.getName())
          || className.startsWith("java.")
          || className.startsWith("jdk.")
          || className.startsWith("sun.")
          || className.startsWith("org.openqa.selenium.")) {
        continue;
      }

      String simpleName = className.substring(className.lastIndexOf('.') + 1);
      if (simpleName.endsWith("Test") || simpleName.endsWith("IT") || simpleName.endsWith("ST")) {
        return simpleName + "." + frame.getMethodName();
      }
    }
    return "TracingSelenideChromeDriver.session";
  }

  private final class TracingWebElement implements WebElement, SearchContext, TakesScreenshot, WrapsElement, WrapsDriver {
    private final WebElement delegate;
    private final String descriptor;

    private TracingWebElement(WebElement delegate, String descriptor) {
      this.delegate = delegate;
      this.descriptor = descriptor;
    }

    @Override
    public void click() {
      traceAction("Click element", delegate, delegate::click);
    }

    @Override
    public void submit() {
      traceAction("Submit element", delegate, delegate::submit);
    }

    @Override
    public void sendKeys(CharSequence... keysToSend) {
      traceAction("Type into element", delegate, () -> delegate.sendKeys(keysToSend));
    }

    @Override
    public void clear() {
      traceAction("Clear element", delegate, delegate::clear);
    }

    @Override
    public String getTagName() {
      return traceQuery("Read tag name", delegate, delegate::getTagName);
    }

    @Override
    public String getAttribute(String name) {
      return traceQuery("Read attribute " + name, delegate, () -> delegate.getAttribute(name));
    }

    @Override
    public String getDomAttribute(String name) {
      return traceQuery("Read DOM attribute " + name, delegate, () -> delegate.getDomAttribute(name));
    }

    @Override
    public String getDomProperty(String name) {
      return traceQuery("Read DOM property " + name, delegate, () -> delegate.getDomProperty(name));
    }

    @Override
    public String getCssValue(String propertyName) {
      return traceQuery("Read CSS value " + propertyName, delegate, () -> delegate.getCssValue(propertyName));
    }

    @Override
    public org.openqa.selenium.Point getLocation() {
      return traceQuery("Read element location", delegate, delegate::getLocation);
    }

    @Override
    public org.openqa.selenium.Dimension getSize() {
      return traceQuery("Read element size", delegate, delegate::getSize);
    }

    @Override
    public org.openqa.selenium.Rectangle getRect() {
      return traceQuery("Read element rectangle", delegate, delegate::getRect);
    }

    @Override
    public boolean isSelected() {
      return traceQuery("Check element selected", delegate, delegate::isSelected);
    }

    @Override
    public boolean isEnabled() {
      return traceQuery("Check element enabled", delegate, delegate::isEnabled);
    }

    @Override
    public String getText() {
      return traceQuery("Read element text", delegate, delegate::getText);
    }

    @Override
    public List<WebElement> findElements(By by) {
      return traceQuery("Find child elements", by, () -> wrapElements(delegate.findElements(by), by.toString()));
    }

    @Override
    public WebElement findElement(By by) {
      return traceQuery("Find child element", by, () -> wrapElement(delegate.findElement(by), by.toString()));
    }

    @Override
    public boolean isDisplayed() {
      return traceQuery("Check element visible", delegate, delegate::isDisplayed);
    }

    @Override
    public org.openqa.selenium.SearchContext getShadowRoot() {
      return traceQuery("Read shadow root", delegate, delegate::getShadowRoot);
    }

    @Override
    public String getAccessibleName() {
      return traceQuery("Read accessible name", delegate, delegate::getAccessibleName);
    }

    @Override
    public String getAriaRole() {
      return traceQuery("Read aria role", delegate, delegate::getAriaRole);
    }

    @Override
    public <X> X getScreenshotAs(OutputType<X> target) throws WebDriverException {
      return traceQuery("Capture element screenshot", delegate, () -> ((TakesScreenshot) delegate).getScreenshotAs(target));
    }

    @Override
    public WebElement getWrappedElement() {
      return delegate;
    }

    @Override
    public WebDriver getWrappedDriver() {
      return TracingSelenideChromeDriver.this;
    }

    @Override
    public String toString() {
      return descriptor == null ? delegate.toString() : descriptor;
    }
  }

  private final class TracingTargetLocator implements TargetLocator {
    private final TargetLocator delegate;

    private TracingTargetLocator(TargetLocator delegate) {
      this.delegate = delegate;
    }

    @Override
    public WebDriver frame(int index) {
      return traceQuery("Switch to frame index " + index, By.tagName("body"), () -> delegate.frame(index));
    }

    @Override
    public WebDriver frame(String nameOrId) {
      return traceQuery("Switch to frame " + nameOrId, By.tagName("body"), () -> delegate.frame(nameOrId));
    }

    @Override
    public WebDriver frame(WebElement frameElement) {
      return traceQuery("Switch to frame element", frameElement, () -> delegate.frame(frameElement));
    }

    @Override
    public WebDriver parentFrame() {
      return traceQuery("Switch to parent frame", By.tagName("body"), delegate::parentFrame);
    }

    @Override
    public WebDriver window(String nameOrHandle) {
      return traceQuery("Switch to window", By.tagName("body"), () -> delegate.window(nameOrHandle));
    }

    @Override
    public WebDriver defaultContent() {
      return traceQuery("Switch to default content", By.tagName("body"), delegate::defaultContent);
    }

    @Override
    public WebElement activeElement() {
      return traceQuery("Read active element", By.tagName("body"), () -> wrapElement(delegate.activeElement(), "activeElement"));
    }

    @Override
    public org.openqa.selenium.Alert alert() {
      return traceQuery("Switch to alert", By.tagName("body"), delegate::alert);
    }

    @Override
    public WebDriver newWindow(WindowType typeHint) {
      return traceQuery("Open new window", By.tagName("body"), () -> delegate.newWindow(typeHint));
    }
  }

  private final class TracingNavigation implements Navigation {
    private final Navigation delegate;

    private TracingNavigation(Navigation delegate) {
      this.delegate = delegate;
    }

    @Override
    public void back() {
      traceAction("Navigate back", By.tagName("body"), delegate::back);
    }

    @Override
    public void forward() {
      traceAction("Navigate forward", By.tagName("body"), delegate::forward);
    }

    @Override
    public void to(String url) {
      traceAction("Navigate to " + url, url, () -> delegate.to(url));
    }

    @Override
    public void to(java.net.URL url) {
      traceAction("Navigate to " + url, url.toString(), () -> delegate.to(url));
    }

    @Override
    public void refresh() {
      traceAction("Refresh page", By.tagName("body"), delegate::refresh);
    }
  }

  private final class TracingOptions implements Options {
    private final Options delegate;

    private TracingOptions(Options delegate) {
      this.delegate = delegate;
    }

    @Override
    public void addCookie(Cookie cookie) {
      traceAction("Add cookie", By.tagName("body"), () -> delegate.addCookie(cookie));
    }

    @Override
    public void deleteCookieNamed(String name) {
      traceAction("Delete cookie " + name, By.tagName("body"), () -> delegate.deleteCookieNamed(name));
    }

    @Override
    public void deleteCookie(Cookie cookie) {
      traceAction("Delete cookie", By.tagName("body"), () -> delegate.deleteCookie(cookie));
    }

    @Override
    public void deleteAllCookies() {
      traceAction("Delete all cookies", By.tagName("body"), delegate::deleteAllCookies);
    }

    @Override
    public Set<Cookie> getCookies() {
      return traceQuery("Read cookies", By.tagName("body"), delegate::getCookies);
    }

    @Override
    public Cookie getCookieNamed(String name) {
      return traceQuery("Read cookie " + name, By.tagName("body"), () -> delegate.getCookieNamed(name));
    }

    @Override
    public Timeouts timeouts() {
      return delegate.timeouts();
    }

    @Override
    public Window window() {
      return delegate.window();
    }

    @Override
    public Logs logs() {
      return delegate.logs();
    }
  }
}

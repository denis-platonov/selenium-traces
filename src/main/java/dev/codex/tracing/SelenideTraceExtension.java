package dev.codex.tracing;

import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class SelenideTraceExtension implements BeforeEachCallback, AfterTestExecutionCallback {
  private final SelenideTraceConfig config;

  public SelenideTraceExtension() {
    this(SelenideTraceConfig.defaultConfig());
  }

  public SelenideTraceExtension(SelenideTraceConfig config) {
    this.config = config;
  }

  @Override
  public void beforeEach(ExtensionContext context) {
    String testName = context.getRequiredTestClass().getSimpleName() + "." + context.getRequiredTestMethod().getName();
    SelenideTrace.start(testName, config);
  }

  @Override
  public void afterTestExecution(ExtensionContext context) {
    Throwable failure = context.getExecutionException().orElse(null);
    if (failure == null) {
      SelenideTrace.stopPassed();
    } else {
      SelenideTrace.stopFailed(failure);
    }
  }
}

package ch.legali.sdk.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "legali.example")
public class ExampleConfig {

  /** Example Connector iterations (per thread) */
  private int iterations;

  /** Optional path for sample pdf files. If blank the sample.pdf form resources will be used. */
  private String filesPath;

  /** cleanup environment before starting: deletes all legal cases added by this example. */
  private boolean cleanup = false;

  public int getIterations() {
    return this.iterations;
  }

  public void setIterations(int iterations) {
    this.iterations = iterations;
  }

  public String getFilesPath() {
    return this.filesPath;
  }

  public void setFilesPath(String filesPath) {
    this.filesPath = filesPath;
  }

  public boolean isCleanup() {
    return this.cleanup;
  }

  public void setCleanup(boolean cleanup) {
    this.cleanup = cleanup;
  }
}

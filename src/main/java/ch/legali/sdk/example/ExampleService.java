package ch.legali.sdk.example;

import ch.legali.sdk.example.config.ExampleConfig;
import ch.legali.sdk.internal.HealthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

/**
 * The ExampleService the ExampleThreads once the connection to the legal-i cloud is established.
 */
@Service
public class ExampleService {
  private static final Logger log = LoggerFactory.getLogger(HealthService.class);

  private final TaskExecutor taskExecutor;
  private final ApplicationContext applicationContext;
  private final ExampleThread exampleThreadBean;
  private final ExampleConfig config;

  public ExampleService(
      TaskExecutor taskExecutor,
      ApplicationContext applicationContext,
      ExampleThread exampleThreadBean,
      ExampleConfig config) {
    this.taskExecutor = taskExecutor;
    this.applicationContext = applicationContext;
    this.exampleThreadBean = exampleThreadBean;
    this.config = config;
  }

  /**
   * Start connector threads on {@link HealthService.StartConnectorEvent)
   */
  @EventListener
  public void onStartConnectorEvent(
      @SuppressWarnings("unused") HealthService.StartConnectorEvent event)
      throws InterruptedException {
    log.info("Received StartConnectorEvent, let's go!");

    // Cleanup deletes all cases uploaded by this agent
    if (this.config.isCleanup()) {
      this.exampleThreadBean.cleanup();
    }

    // run example connector threads according to tasks pool
    final int threadPoolSize = ((ThreadPoolTaskExecutor) this.taskExecutor).getMaxPoolSize();
    for (int i = 0; i < threadPoolSize; i++) {
      Thread.sleep(500);
      ExampleThread connector = this.applicationContext.getBean(ExampleThread.class);
      this.taskExecutor.execute(connector);
    }
  }
}

package ch.legali.sdk.example;

import ch.legali.api.events.ExportCreatedEvent;
import ch.legali.api.events.ExportSharedEvent;
import ch.legali.api.events.ExportViewedEvent;
import ch.legali.api.events.LegalCaseCreatedEvent;
import ch.legali.api.events.LegalCaseReadyEvent;
import ch.legali.api.events.LegalCaseStatusChangedEvent;
import ch.legali.api.events.LegalCaseUpdatedEvent;
import ch.legali.api.events.PongEvent;
import ch.legali.api.events.SourceFileCreatedEvent;
import ch.legali.api.events.SourceFileTaskFailedEvent;
import ch.legali.api.events.SourceFileUpdatedEvent;
import ch.legali.sdk.internal.HealthService;
import ch.legali.sdk.requests.ConfirmEventRequest;
import ch.legali.sdk.requests.PingRequest;
import ch.legali.sdk.services.EventService;
import ch.legali.sdk.services.FileDownloadService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/** This service is used to react to events form the legal-i cloud. */
@Service
public class ExampleRemoteEventService {
  private static final Logger log = LoggerFactory.getLogger(ExampleRemoteEventService.class);
  private final FileDownloadService fileDownloadService;
  private final EventService eventService;

  public ExampleRemoteEventService(
      FileDownloadService fileDownloadService, EventService eventService) {
    this.fileDownloadService = fileDownloadService;
    this.eventService = eventService;
  }

  @PostConstruct
  public void init() {
    // NOTE: all events that the agent subscribes to, need to be handled by an event listener.
    this.eventService.subscribe(
        PongEvent.class,

        // legalcase CRUD through frontend
        LegalCaseCreatedEvent.class,
        LegalCaseStatusChangedEvent.class,
        LegalCaseUpdatedEvent.class,

        // all sourcefiles processed
        LegalCaseReadyEvent.class,

        // sourcefiles CRUD through frontend
        SourceFileCreatedEvent.class,
        SourceFileUpdatedEvent.class,

        // processing error
        SourceFileTaskFailedEvent.class,

        // export
        ExportCreatedEvent.class,
        ExportSharedEvent.class,
        ExportViewedEvent.class);
  }

  /** On connector start, ping the API to request a pong event */
  @EventListener
  public void onStartConnectorEvent(
      @SuppressWarnings("unused") HealthService.StartConnectorEvent event) {
    log.info("🏓 Requesting a pong remote event");
    this.eventService.ping(new PingRequest("ping"));
  }

  /*
   * NOTE: all events that the agent subscribes to, need to be handled by an event listener.
   */

  @EventListener
  public void handle(PongEvent event) {
    log.info("🏓 PingPong Event received: " + "\nid " + event.getUuid());
    this.eventService.confirm(new ConfirmEventRequest(event.getUuid()));
  }

  // legalcase handlers
  @EventListener
  public void handle(LegalCaseCreatedEvent event) {
    log.info(
        "LegalCaseCreatedEvent: "
            + "\n"
            + event.getLegalCase().getFirstname()
            + " "
            + event.getLegalCase().getLastname());
    this.eventService.confirm(new ConfirmEventRequest(event.getUuid()));
  }

  @EventListener
  public void handle(LegalCaseStatusChangedEvent event) {
    log.info(
        "LegalCaseStatusChangedEvent: "
            + "\n"
            + event.getLegalCaseUuid()
            + " "
            + event.getStatus());
    this.eventService.confirm(new ConfirmEventRequest(event.getUuid()));
  }

  @EventListener
  public void handle(LegalCaseUpdatedEvent event) {
    log.info(
        "LegalCaseUpdatedEvent: "
            + "\n"
            + event.getLegalCase().getFirstname()
            + " "
            + event.getLegalCase().getLastname());
    this.eventService.confirm(new ConfirmEventRequest(event.getUuid()));
  }

  @EventListener
  public void handle(LegalCaseReadyEvent event) {
    log.info("LegalCaseReadyEvent: " + "\n" + event.getLegalCaseUuid());
    this.eventService.confirm(new ConfirmEventRequest(event.getUuid()));
  }

  // sourcefiles handler

  @EventListener
  public void handle(SourceFileCreatedEvent event) {
    log.info("SourceFileCreatedEvent: " + "\n" + event.getSourceFile().getSourceFileUUID());
    this.eventService.confirm(new ConfirmEventRequest(event.getUuid()));
  }

  @EventListener
  public void handle(SourceFileUpdatedEvent event) {
    log.info("SourceFileUpdatedEvent: " + "\n" + event.getField());
    this.eventService.confirm(new ConfirmEventRequest(event.getUuid()));
  }

  @EventListener
  public void handle(SourceFileTaskFailedEvent event) {
    log.info("SourceFileTaskFailedEvent: " + "\n" + event.getSourceFileUuid());
    this.eventService.confirm(new ConfirmEventRequest(event.getUuid()));
  }

  @EventListener
  public void handle(ExportCreatedEvent event) {
    log.info("🍻  ExportCreatedEvent: " + event.getExportUuid());
    log.info("    Recipient : " + event.getRecipient());
    log.info("    Case Id   : " + event.getLegalCaseUuid());
    log.info("    Timestamp : " + event.getTs());

    File file = this.fileDownloadService.downloadFile(event.getFileUri());
    try {
      Files.copy(file.toPath(), Path.of("./dummy.pdf"), StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      e.printStackTrace();
    }
    log.info("⤵️  Downloaded file: {}, Size: {}", file.getName(), file.length());

    this.eventService.confirm(new ConfirmEventRequest(event.getUuid()));
  }

  @EventListener
  public void handle(ExportSharedEvent event) {
    log.info(
        "✉️ ExportSharedEvent: "
            + event.getExportUuid()
            + "\n"
            + event.getMethod()
            + "\n"
            + event.getLink()
            + "\n"
            + event.getEmail());
    this.eventService.confirm(new ConfirmEventRequest(event.getUuid()));
  }

  @EventListener
  public void handle(ExportViewedEvent event) {
    log.info(
        "📖 ExportViewedEvent: " + "\n" + event.getLegalCaseUuid() + " " + event.getOpenedBy());
    this.eventService.confirm(new ConfirmEventRequest(event.getUuid()));
  }
}

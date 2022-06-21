package ch.legali.sdk.example;

import ch.legali.sdk.example.config.ExampleConfig;
import ch.legali.sdk.exceptions.FileConflictException;
import ch.legali.sdk.exceptions.NotFoundException;
import ch.legali.sdk.models.AgentLegalCaseDTO;
import ch.legali.sdk.models.AgentSourceFileDTO;
import ch.legali.sdk.models.AgentSourceFileDTO.SourceFileStatus;
import ch.legali.sdk.services.LegalCaseService;
import ch.legali.sdk.services.SourceFileService;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Note that the connector API is thread-safe. */
@Component
public class ExampleThread implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(ExampleThread.class);

  private final LegalCaseService legalCaseService;
  private final SourceFileService sourceFileService;
  private final ExampleConfig exampleConfig;

  public ExampleThread(
      LegalCaseService legalCaseService,
      SourceFileService sourceFileService,
      ExampleConfig exampleConfig) {
    this.legalCaseService = legalCaseService;
    this.sourceFileService = sourceFileService;
    this.exampleConfig = exampleConfig;
  }

  @Override
  public void run() {
    int i = 0;
    while (i++ < this.exampleConfig.getIterations()) {
      log.info("🚀  Starting run {}", i);
      this.runExample();
    }
  }

  /**
   * This is the dummy logic of the connector. You can download and check JavaDoc of each method and
   * entity of the SDK.
   */
  private void runExample() {
    // Create
    log.info("🗂  Adding LegalCase");
    AgentLegalCaseDTO legalCase =
        AgentLegalCaseDTO.builder()
            .legalCaseUUID(UUID.randomUUID())
            .firstname("John")
            .lastname("Doe")
            .reference("123-456-789")
            .owner("DummyIamUser")
            .addGroups("group1")
            .putMetadata("meta.dummy", "dummy value")
            .build();
    this.legalCaseService.create(legalCase);

    // update legal case
    log.info("🤓  Updating LegalCase");
    AgentLegalCaseDTO legalCaseResponse = this.legalCaseService.get(legalCase.getLegalCaseUUID());
    AgentLegalCaseDTO nameChanged =
        AgentLegalCaseDTO.builder()
            .from(legalCaseResponse)
            .firstname("Jane")
            .reference("John changed his name")
            .build();
    this.legalCaseService.update(nameChanged);

    /*
     * To keep a constant memory footprint on the agent, the SDK uses a FileObject and not a ByteArrayResource.
     * PDF files can be large if they contain images (> 500MB), in multi-threaded mode this leads to unwanted spikes in
     * memory usage.
     * Ideally the files are chunked downloaded to a temporary file and then passed to the SDK.
     */
    final File fileToUpload = chooseLocalFile();

    // add / delete a sourcefile
    AgentSourceFileDTO sourceFile =
        AgentSourceFileDTO.builder()
            .sourceFileUUID(UUID.randomUUID())
            .legalCaseUUID(legalCase.getLegalCaseUUID())
            .reference("hello.pdf")
            .putMetadata("hello", "world")
            .putMetadata("legali.title", "Sample Document")
            .putMetadata("legali.dossiertype", this.chooseDossierType())
            .putMetadata("legali.doctype", this.chooseDocType())
            .putMetadata("legali.issuedate", "2012-12-12")
            .build();

    log.info("🧾  Creating SourceFile");
    this.sourceFileService.create(sourceFile, fileToUpload);

    log.info("😴  Waiting for SourceFile to be processed  (will timeout after 3 seconds!)");
    // NOTE: use with care, busy waiting and usually not required
    SourceFileStatus status =
        this.sourceFileService.waitForSourceFileReadyOrTimeout(
            sourceFile.getSourceFileUUID(), TimeUnit.SECONDS.toSeconds(3));

    // NOTE: will always time out, if processing is disabled
    if (status.equals(SourceFileStatus.ERROR) || status.equals(SourceFileStatus.TIMEOUT)) {
      log.warn(
          "💥 legal-i was not fast enough to process this file {}", sourceFile.getSourceFileUUID());
    }

    // Try to create same sourcefile with another file
    try {
      ClassPathResource cp = new ClassPathResource("sample2.pdf");
      try {
        File file2 = cp.getFile();
        this.sourceFileService.create(sourceFile, file2);
      } catch (IOException e) {
        log.error("🙅‍  Failed to open sample2.pdf file", e);
      }
    } catch (FileConflictException fileConflictException) {
      log.info("🙅‍  Sourcefile file are different, refused to do something!‍️");
    }
    log.info("🧾  Creating the same SourceFile AGAIN (creates are idempotent)");
    this.sourceFileService.create(sourceFile, fileToUpload);

    List<AgentSourceFileDTO> list =
        this.sourceFileService.getByLegalCase(legalCase.getLegalCaseUUID());
    log.info("1️⃣ LegalCase has {} source files", list.size());

    log.info("␡ Deleting SourceFile");
    this.sourceFileService.delete(sourceFile.getSourceFileUUID());

    list = this.sourceFileService.getByLegalCase(legalCase.getLegalCaseUUID());
    log.info("😅  LegalCase has {} source files", list.size());

    log.info("🗄  Archiving LegalCase");
    this.legalCaseService.archive(legalCaseResponse.getLegalCaseUUID());

    log.info("🗑  Deleting LegalCase");
    this.legalCaseService.delete(legalCaseResponse.getLegalCaseUUID());

    try {
      this.legalCaseService.get(legalCase.getLegalCaseUUID());
    } catch (NotFoundException ignored) {
      log.info("🥳  LegalCase has successfully been deleted, well done!");
    }
  }

  public void cleanup() {
    List<AgentLegalCaseDTO> allCases = this.legalCaseService.list();
    for (AgentLegalCaseDTO currentLegalCase : allCases) {
      if ("example-agent"
          .equals(currentLegalCase.getMetadata().getOrDefault("legali.uploader", ""))) {
        log.info("🧹 Cleaning up {}", currentLegalCase.getLegalCaseUUID());
        this.legalCaseService.delete(currentLegalCase.getLegalCaseUUID());
      }
    }
  }

  /**
   * Returns either a random file from the given directory or the sample.pdf
   *
   * @return File
   */
  private File chooseLocalFile() {
    // NOTE: if a directory has been specified, the connector loads a random file form there
    if (this.exampleConfig.getFilesPath() != null && !this.exampleConfig.getFilesPath().isBlank()) {
      final File[] files = new File(this.exampleConfig.getFilesPath()).listFiles();
      if (files != null) {
        final File f = files[(int) Math.floor(Math.random() * files.length)];
        log.info(
            "Chosen file {}, {} MB", f.getName(), Math.round((double) f.length() / (1024 * 1024)));
        return f;
      }
    }

    // fall back to sample, if no or invalid path specified
    log.debug("Using sample.pdf");
    ClassPathResource cp = new ClassPathResource("sample.pdf");
    File file;
    try {
      file = cp.getFile();
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
    return file;
  }

  /** @return String random doc type */
  private String chooseDocType() {
    return List.of("type_medical", "type_financial_ik_statement", "type_legal_disposition")
        .get((int) Math.floor(Math.random() * 3));
  }

  /** @return String random dossier type */
  private String chooseDossierType() {
    return List.of("accident", "liability", "iv-be").get((int) Math.floor(Math.random() * 3));
  }
}

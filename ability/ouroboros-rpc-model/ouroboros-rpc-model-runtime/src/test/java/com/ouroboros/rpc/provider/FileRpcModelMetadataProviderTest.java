package com.ouroboros.rpc.provider;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ouroboros.AppContext;
import com.ouroboros.rpc.metadata.RPCModelMetadata;

class FileRpcModelMetadataProviderTest {

  private final Logger logger = (Logger) LoggerFactory.getLogger(FileRpcModelMetadataProvider.class);

  @TempDir
  Path tempDir;

  private ListAppender<ILoggingEvent> logAppender;

  @AfterEach
  void tearDownAppender() {
    if (logAppender != null) {
      logger.detachAppender(logAppender);
      logAppender.stop();
      logAppender = null;
    }
  }

  @Test
  void missingMetadataDirectory_returnsEmptyListWithoutWarning() {
    logAppender = attachLogAppender();
    FileRpcModelMetadataProvider provider = new FileRpcModelMetadataProvider(appContext(tempDir));

    List<RPCModelMetadata> metadataList = provider.getAllRpcModelMetadata();

    assertNotNull(metadataList);
    assertTrue(metadataList.isEmpty());
    assertTrue(warningEvents().isEmpty());
  }

  @Test
  void emptyFile_isSkippedWhileValidFileLoads() throws IOException {
    logAppender = attachLogAppender();
    Path metadataDir = Files.createDirectories(tempDir.resolve("rpc-model"));
    Files.write(metadataDir.resolve("empty.rpc.json"), new byte[0]);
    writeValidMetadata(metadataDir.resolve("valid.rpc.json"), "valid-empty");

    FileRpcModelMetadataProvider provider = new FileRpcModelMetadataProvider(appContext(tempDir));

    List<RPCModelMetadata> metadataList = provider.getAllRpcModelMetadata();

    assertEquals(1, metadataList.size());
    assertEquals("valid-empty", metadataList.get(0).getName());
    ILoggingEvent warning = assertSingleWarningContaining("empty.rpc.json");
    assertTrue(warning.getFormattedMessage().contains("because it is empty"));
    assertThrowableMessageEquals(warning, "empty.rpc.json is empty");
  }

  @Test
  void invalidJsonFile_isSkippedWhileValidFileLoads() throws IOException {
    logAppender = attachLogAppender();
    Path metadataDir = Files.createDirectories(tempDir.resolve("rpc-model"));
    Files.write(metadataDir.resolve("invalid.rpc.json"), "{".getBytes(StandardCharsets.UTF_8));
    writeValidMetadata(metadataDir.resolve("valid.rpc.json"), "valid-json");

    FileRpcModelMetadataProvider provider = new FileRpcModelMetadataProvider(appContext(tempDir));

    List<RPCModelMetadata> metadataList = provider.getAllRpcModelMetadata();

    assertEquals(1, metadataList.size());
    assertEquals("valid-json", metadataList.get(0).getName());
    ILoggingEvent warning = assertSingleWarningContaining("invalid.rpc.json");
    assertTrue(warning.getFormattedMessage().contains("because JSON parsing failed"));
    assertThrowableMessageNotBlank(warning);
  }

  @Test
  void readFailure_isSkippedWhileValidFileLoads() throws IOException {
    logAppender = attachLogAppender();
    Path metadataDir = Files.createDirectories(tempDir.resolve("rpc-model"));
    Path failingFile = metadataDir.resolve("broken.rpc.json");
    writeValidMetadata(failingFile, "broken-ignored");
    writeValidMetadata(metadataDir.resolve("valid.rpc.json"), "valid-read");
    IOException readFailure = new IOException("forced read failure");

    FileRpcModelMetadataProvider provider = new TestFileRpcModelMetadataProvider(appContext(tempDir), failingFile, readFailure,
        false);

    List<RPCModelMetadata> metadataList = provider.getAllRpcModelMetadata();

    assertEquals(1, metadataList.size());
    assertEquals("valid-read", metadataList.get(0).getName());
    ILoggingEvent warning = assertSingleWarningContaining("broken.rpc.json");
    assertTrue(warning.getFormattedMessage().contains("because it could not be read"));
    assertThrowableMessageEquals(warning, "forced read failure");
  }

  @Test
  void scanFailure_returnsEmptyListAndWarnsOnce() throws IOException {
    logAppender = attachLogAppender();
    Files.createDirectories(tempDir.resolve("rpc-model"));
    FileRpcModelMetadataProvider provider = new TestFileRpcModelMetadataProvider(appContext(tempDir), null,
        new IOException("forced scan failure"), true);

    List<RPCModelMetadata> metadataList = provider.getAllRpcModelMetadata();

    assertNotNull(metadataList);
    assertEquals(Collections.emptyList(), metadataList);
    ILoggingEvent warning = assertSingleWarningContaining(tempDir.resolve("rpc-model").toString());
    assertTrue(warning.getFormattedMessage().contains("Failed to scan RPC model metadata directory"));
    assertThrowableMessageEquals(warning, "forced scan failure");
  }

  private void writeValidMetadata(Path path, String name) throws IOException {
    String content = "{"
        + "\"name\":\"" + name + "\","
        + "\"title\":\"" + name + "\","
        + "\"type\":\"OpenApi\","
        + "\"isEnabled\":true,"
        + "\"request\":{}"
        + "}";
    Files.write(path, content.getBytes(StandardCharsets.UTF_8));
  }

  private AppContext appContext(Path homePath) {
    return () -> homePath.toString();
  }

  private ListAppender<ILoggingEvent> attachLogAppender() {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    return appender;
  }

  private List<ILoggingEvent> warningEvents() {
    return logAppender.list.stream()
        .filter(event -> event.getLevel() == Level.WARN)
        .collect(Collectors.toList());
  }

  private ILoggingEvent assertSingleWarningContaining(String locationToken) {
    List<ILoggingEvent> warnings = warningEvents();
    assertEquals(1, warnings.size());
    ILoggingEvent warning = warnings.get(0);
    assertTrue(warning.getFormattedMessage().contains(locationToken));
    return warning;
  }

  private void assertThrowableMessageEquals(ILoggingEvent warning, String expectedMessage) {
    assertNotNull(warning.getThrowableProxy());
    assertEquals(expectedMessage, warning.getThrowableProxy().getMessage());
  }

  private void assertThrowableMessageNotBlank(ILoggingEvent warning) {
    assertNotNull(warning.getThrowableProxy());
    String message = warning.getThrowableProxy().getMessage();
    assertNotNull(message);
    assertFalse(message.trim().isEmpty());
  }

  private static class TestFileRpcModelMetadataProvider extends FileRpcModelMetadataProvider {
    private final Path failingFile;
    private final IOException failure;
    private final boolean failScan;

    private TestFileRpcModelMetadataProvider(AppContext appContext, Path failingFile, IOException failure, boolean failScan) {
      super(appContext);
      this.failingFile = failingFile;
      this.failure = failure;
      this.failScan = failScan;
    }

    @Override
    protected Stream<Path> walkMetadataFiles(Path metadataHome) throws IOException {
      if (failScan) {
        throw failure;
      }
      return super.walkMetadataFiles(metadataHome);
    }

    @Override
    protected String readFileContent(Path filePath) throws IOException {
      if (failingFile != null && failingFile.equals(filePath)) {
        throw failure;
      }
      return super.readFileContent(filePath);
    }
  }
}

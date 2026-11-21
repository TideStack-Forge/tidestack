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
import com.ouroboros.rpc.security.metadata.RPCCredentialMetadata;

class FileRpcAuthCredentialMetadataProviderTest {

  private final Logger logger = (Logger) LoggerFactory.getLogger(FileRpcAuthCredentialMetadataProvider.class);

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
    FileRpcAuthCredentialMetadataProvider provider = new FileRpcAuthCredentialMetadataProvider(appContext(tempDir));

    List<RPCCredentialMetadata> metadataList = provider.getAllRpcAuthenticatorMetadata();

    assertNotNull(metadataList);
    assertTrue(metadataList.isEmpty());
    assertTrue(diagnosticEvents().isEmpty());
  }

  @Test
  void emptyFile_isSkippedWhileValidFileLoads() throws IOException {
    logAppender = attachLogAppender();
    Path metadataDir = Files.createDirectories(tempDir.resolve("rpc-model/auth"));
    Files.write(metadataDir.resolve("empty.rpc.auth.json"), new byte[0]);
    writeValidMetadata(metadataDir.resolve("valid.rpc.auth.json"), "valid-empty");

    FileRpcAuthCredentialMetadataProvider provider = new FileRpcAuthCredentialMetadataProvider(appContext(tempDir));

    List<RPCCredentialMetadata> metadataList = provider.getAllRpcAuthenticatorMetadata();

    assertEquals(1, metadataList.size());
    assertEquals("valid-empty", metadataList.get(0).getName());
    ILoggingEvent warning = assertSingleWarningContaining("empty.rpc.auth.json");
    assertTrue(warning.getFormattedMessage().contains("because it is empty"));
    assertThrowableMessageEquals(warning, "empty.rpc.auth.json is empty");
  }

  @Test
  void invalidJsonFile_isSkippedWhileValidFileLoads() throws IOException {
    logAppender = attachLogAppender();
    Path metadataDir = Files.createDirectories(tempDir.resolve("rpc-model/auth"));
    Files.write(metadataDir.resolve("invalid.rpc.auth.json"), "{".getBytes(StandardCharsets.UTF_8));
    writeValidMetadata(metadataDir.resolve("valid.rpc.auth.json"), "valid-json");

    FileRpcAuthCredentialMetadataProvider provider = new FileRpcAuthCredentialMetadataProvider(appContext(tempDir));

    List<RPCCredentialMetadata> metadataList = provider.getAllRpcAuthenticatorMetadata();

    assertEquals(1, metadataList.size());
    assertEquals("valid-json", metadataList.get(0).getName());
    ILoggingEvent warning = assertSingleWarningContaining("invalid.rpc.auth.json");
    assertTrue(warning.getFormattedMessage().contains("because JSON parsing failed"));
    assertThrowableMessageNotBlank(warning);
  }

  @Test
  void readFailure_isSkippedWhileValidFileLoads() throws IOException {
    logAppender = attachLogAppender();
    Path metadataDir = Files.createDirectories(tempDir.resolve("rpc-model/auth"));
    Path failingFile = metadataDir.resolve("broken.rpc.auth.json");
    writeValidMetadata(failingFile, "broken-ignored");
    writeValidMetadata(metadataDir.resolve("valid.rpc.auth.json"), "valid-read");
    IOException readFailure = new IOException("forced read failure");

    FileRpcAuthCredentialMetadataProvider provider = new TestFileRpcAuthCredentialMetadataProvider(appContext(tempDir),
        failingFile, readFailure, false);

    List<RPCCredentialMetadata> metadataList = provider.getAllRpcAuthenticatorMetadata();

    assertEquals(1, metadataList.size());
    assertEquals("valid-read", metadataList.get(0).getName());
    ILoggingEvent warning = assertSingleWarningContaining("broken.rpc.auth.json");
    assertTrue(warning.getFormattedMessage().contains("because it could not be read"));
    assertThrowableMessageEquals(warning, "forced read failure");
  }

  @Test
  void scanFailure_returnsEmptyListAndWarnsOnce() throws IOException {
    logAppender = attachLogAppender();
    Files.createDirectories(tempDir.resolve("rpc-model/auth"));
    FileRpcAuthCredentialMetadataProvider provider = new TestFileRpcAuthCredentialMetadataProvider(appContext(tempDir),
        null, new IOException("forced scan failure"), true);

    List<RPCCredentialMetadata> metadataList = provider.getAllRpcAuthenticatorMetadata();

    assertNotNull(metadataList);
    assertEquals(Collections.emptyList(), metadataList);
    ILoggingEvent warning = assertSingleWarningContaining(tempDir.resolve("rpc-model/auth").toString());
    assertTrue(warning.getFormattedMessage().contains("Failed to scan RPC auth credential metadata directory"));
    assertThrowableMessageEquals(warning, "forced scan failure");
  }

  private void writeValidMetadata(Path path, String name) throws IOException {
    String content = "{"
        + "\"name\":\"" + name + "\","
        + "\"title\":\"" + name + "\","
        + "\"type\":\"apikey\","
        + "\"isEnabled\":true,"
        + "\"description\":\"" + name + "\""
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

  private List<ILoggingEvent> diagnosticEvents() {
    return logAppender.list.stream()
        .filter(event -> event.getLevel().isGreaterOrEqual(Level.WARN))
        .collect(Collectors.toList());
  }

  private ILoggingEvent assertSingleWarningContaining(String locationToken) {
    List<ILoggingEvent> events = diagnosticEvents();
    assertEquals(1, events.size());
    ILoggingEvent warning = events.get(0);
    assertEquals(Level.WARN, warning.getLevel());
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

  private static class TestFileRpcAuthCredentialMetadataProvider extends FileRpcAuthCredentialMetadataProvider {
    private final Path failingFile;
    private final IOException failure;
    private final boolean failScan;

    private TestFileRpcAuthCredentialMetadataProvider(AppContext appContext, Path failingFile, IOException failure,
        boolean failScan) {
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

package com.ouroboros.logicflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
import com.ouroboros.logicflow.model.FlowModelMeta;

class AppLogicModelMetaProviderTest {

  private final Logger logger = (Logger) LoggerFactory.getLogger(AppLogicModelMetaProvider.class);

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
  void missingMetadataDirectoryReturnsEmptyMapWithoutWarning() {
    logAppender = attachLogAppender();
    AppLogicModelMetaProvider provider = new AppLogicModelMetaProvider(appContext(tempDir));

    Map<String, FlowModelMeta> modelMetas = provider.getFlowModelMetas();

    assertNotNull(modelMetas);
    assertEquals(Collections.emptyMap(), modelMetas);
    assertTrue(warningEvents().isEmpty());
  }

  @Test
  void invalidJsonFileIsSkippedWhileValidFileLoads() throws IOException {
    logAppender = attachLogAppender();
    Path metadataDir = Files.createDirectories(tempDir.resolve("logic-flow"));
    Files.write(metadataDir.resolve("invalid.json"), "{".getBytes(StandardCharsets.UTF_8));
    writeValidFlow(metadataDir.resolve("valid.json"), "valid.flow");

    AppLogicModelMetaProvider provider = new AppLogicModelMetaProvider(appContext(tempDir));

    Map<String, FlowModelMeta> modelMetas = provider.getFlowModelMetas();

    assertEquals(1, modelMetas.size());
    assertTrue(modelMetas.containsKey("valid.flow"));
    ILoggingEvent warning = assertSingleWarningContaining("invalid.json");
    assertTrue(warning.getFormattedMessage().contains("JSON 解析失败"));
    assertThrowableMessageNotBlank(warning);
  }

  @Test
  void readFailureIsSkippedWhileValidFileLoads() throws IOException {
    logAppender = attachLogAppender();
    Path metadataDir = Files.createDirectories(tempDir.resolve("logic-flow"));
    Path failingFile = metadataDir.resolve("broken.json");
    writeValidFlow(failingFile, "broken.flow");
    writeValidFlow(metadataDir.resolve("valid.json"), "valid.flow");
    IOException readFailure = new IOException("forced read failure");

    AppLogicModelMetaProvider provider = new TestAppLogicModelMetaProvider(appContext(tempDir), failingFile, readFailure, false);

    Map<String, FlowModelMeta> modelMetas = provider.getFlowModelMetas();

    assertEquals(1, modelMetas.size());
    assertTrue(modelMetas.containsKey("valid.flow"));
    ILoggingEvent warning = assertSingleWarningContaining("broken.json");
    assertTrue(warning.getFormattedMessage().contains("读取失败"));
    assertThrowableMessageEquals(warning, "forced read failure");
  }

  @Test
  void scanFailureReturnsEmptyMapAndWarnsOnce() throws IOException {
    logAppender = attachLogAppender();
    Files.createDirectories(tempDir.resolve("logic-flow"));
    AppLogicModelMetaProvider provider = new TestAppLogicModelMetaProvider(appContext(tempDir), null,
        new IOException("forced scan failure"), true);

    Map<String, FlowModelMeta> modelMetas = provider.getFlowModelMetas();

    assertNotNull(modelMetas);
    assertEquals(Collections.emptyMap(), modelMetas);
    ILoggingEvent warning = assertSingleWarningContaining(tempDir.resolve("logic-flow").toString());
    assertTrue(warning.getFormattedMessage().contains("扫描逻辑流元数据目录"));
    assertThrowableMessageEquals(warning, "forced scan failure");
  }

  @Test
  void missingFlowNameIsSkippedWhileOtherFilesStillLoad() throws IOException {
    logAppender = attachLogAppender();
    Path metadataDir = Files.createDirectories(tempDir.resolve("logic-flow"));
    Files.write(metadataDir.resolve("missing-name.json"), "{}".getBytes(StandardCharsets.UTF_8));
    writeValidFlow(metadataDir.resolve("valid.json"), "valid.flow");

    AppLogicModelMetaProvider provider = new AppLogicModelMetaProvider(appContext(tempDir));

    Map<String, FlowModelMeta> modelMetas = provider.getFlowModelMetas();

    assertEquals(1, modelMetas.size());
    assertTrue(modelMetas.containsKey("valid.flow"));
    ILoggingEvent warning = assertSingleWarningContaining("missing-name.json");
    assertTrue(warning.getFormattedMessage().contains("缺少逻辑流名称"));
    assertThrowableMessageEquals(warning, "missing-name.json is missing flow name");
  }

  private void writeValidFlow(Path path, String name) throws IOException {
    String content = "{"
        + "\"name\":\"" + name + "\","
        + "\"entryNode\":\"start\","
        + "\"exitNode\":\"end\","
        + "\"nodes\":[],"
        + "\"connections\":[]"
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

  private static class TestAppLogicModelMetaProvider extends AppLogicModelMetaProvider {
    private final Path failingFile;
    private final IOException failure;
    private final boolean failScan;

    private TestAppLogicModelMetaProvider(AppContext appContext, Path failingFile, IOException failure, boolean failScan) {
      super(appContext, false);
      this.failingFile = failingFile;
      this.failure = failure;
      this.failScan = failScan;
      refresh();
    }

    @Override
    protected Stream<Path> walkModelFiles(Path metadataHome) throws IOException {
      if (failScan) {
        throw failure;
      }
      return super.walkModelFiles(metadataHome);
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

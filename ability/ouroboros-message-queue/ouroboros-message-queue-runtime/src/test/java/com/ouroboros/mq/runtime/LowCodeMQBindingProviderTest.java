package com.ouroboros.mq.runtime;

import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ouroboros.AppContext;
import com.ouroboros.mq.binding.MQBindingProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LowCodeMQBindingProviderTest {

  private final Logger logger = (Logger) LoggerFactory.getLogger(LowCodeMQBindingProvider.class);

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
  void missingBindingDirectoryReturnsEmptyListWithoutErrors() {
    logAppender = attachLogAppender();
    LowCodeMQBindingProvider provider = provider(tempDir);

    List<MQBindingProperties<?>> bindings = provider.getAllBindings();

    assertNotNull(bindings);
    assertTrue(bindings.isEmpty());
    assertTrue(errorEvents().isEmpty());
  }

  @Test
  void emptyFileIsSkippedWhileValidBindingLoads() throws Exception {
    logAppender = attachLogAppender();
    Path bindingDir = Files.createDirectories(tempDir.resolve("mq-binding"));
    Files.write(bindingDir.resolve("empty.mq.json"), new byte[0]);
    Files.write(bindingDir.resolve("valid.mq.json"), validProducerJson("valid-binding").getBytes(StandardCharsets.UTF_8));

    LowCodeMQBindingProvider provider = provider(tempDir);

    List<MQBindingProperties<?>> bindings = provider.getAllBindings();

    assertEquals(1, bindings.size());
    assertEquals("valid-binding", bindings.get(0).getBindingName());
    ILoggingEvent error = assertSingleErrorContaining("empty.mq.json");
    assertTrue(error.getFormattedMessage().contains("消息队列绑定定义"));
    assertTrue(error.getFormattedMessage().contains("为空"));
    assertEquals("消息队列绑定定义 " + bindingDir.resolve("empty.mq.json") + " 为空", error.getThrowableProxy().getMessage());
  }

  @Test
  void invalidJsonFileIsSkippedWhileValidBindingLoads() throws Exception {
    logAppender = attachLogAppender();
    Path bindingDir = Files.createDirectories(tempDir.resolve("mq-binding"));
    Files.write(bindingDir.resolve("invalid.mq.json"), "{".getBytes(StandardCharsets.UTF_8));
    Files.write(bindingDir.resolve("valid.mq.json"), validProducerJson("valid-binding").getBytes(StandardCharsets.UTF_8));

    LowCodeMQBindingProvider provider = provider(tempDir);

    List<MQBindingProperties<?>> bindings = provider.getAllBindings();

    assertEquals(1, bindings.size());
    assertEquals("valid-binding", bindings.get(0).getBindingName());
    ILoggingEvent error = assertSingleErrorContaining("invalid.mq.json");
    assertTrue(error.getFormattedMessage().contains("解析消息队列绑定定义"));
    assertTrue(error.getFormattedMessage().contains("失败"));
    assertNotNull(error.getThrowableProxy());
    assertNotNull(error.getThrowableProxy().getMessage());
    assertTrue(!error.getThrowableProxy().getMessage().trim().isEmpty());
  }

  @Test
  void undecodableBindingFileIsLoggedAsReadFailureAndSkipped() throws Exception {
    logAppender = attachLogAppender();
    Path bindingDir = Files.createDirectories(tempDir.resolve("mq-binding"));
    Files.write(bindingDir.resolve("undecodable.mq.json"), new byte[] {(byte) 0xC3, (byte) 0x28});
    Files.write(bindingDir.resolve("valid.mq.json"), validProducerJson("valid-binding").getBytes(StandardCharsets.UTF_8));

    LowCodeMQBindingProvider provider = provider(tempDir);

    List<MQBindingProperties<?>> bindings = provider.getAllBindings();

    assertEquals(1, bindings.size());
    assertEquals("valid-binding", bindings.get(0).getBindingName());
    ILoggingEvent error = assertSingleErrorContaining("undecodable.mq.json");
    assertTrue(error.getFormattedMessage().contains("读取消息队列绑定定义"));
    assertTrue(error.getFormattedMessage().contains("失败"));
    assertNotNull(error.getThrowableProxy());
    assertNotNull(error.getThrowableProxy().getMessage());
    assertTrue(!error.getThrowableProxy().getMessage().trim().isEmpty());
  }

  @Test
  void undecodableBindingFileIsReadFailureEvenWhenHostDefaultCharsetIsNotUtf8() throws Exception {
    Path bindingDir = Files.createDirectories(tempDir.resolve("mq-binding"));
    Files.write(bindingDir.resolve("undecodable.mq.json"), new byte[] {(byte) 0xC3, (byte) 0x28});
    Files.write(bindingDir.resolve("valid.mq.json"), validProducerJson("valid-binding").getBytes(StandardCharsets.UTF_8));

    CharsetProbeResult result = runCharsetProbe("ISO-8859-1", tempDir);

    assertEquals(0, result.exitCode, result.output);
    assertTrue(result.output.contains("DEFAULT_CHARSET=ISO-8859-1"), result.output);
    assertTrue(result.output.contains("CLASSIFICATION=READ_FAILURE"), result.output);
    assertTrue(result.output.contains("BINDINGS=1"), result.output);
  }

  @Test
  void refreshRebuildsBindingsFromFilesystemAfterCacheWarmup() throws Exception {
    Path bindingDir = Files.createDirectories(tempDir.resolve("mq-binding"));
    Path bindingFile = bindingDir.resolve("valid.mq.json");
    Files.write(bindingFile, validProducerJson("initial-binding").getBytes(StandardCharsets.UTF_8));
    LowCodeMQBindingProvider provider = provider(tempDir);

    List<MQBindingProperties<?>> initialBindings = provider.getAllBindings();

    assertEquals(1, initialBindings.size());
    assertEquals("initial-binding", initialBindings.get(0).getBindingName());

    Files.write(bindingFile, validProducerJson("refreshed-binding").getBytes(StandardCharsets.UTF_8));

    refresh(provider);

    List<MQBindingProperties<?>> refreshedBindings = provider.getAllBindings();

    assertEquals(1, refreshedBindings.size());
    assertEquals("refreshed-binding", refreshedBindings.get(0).getBindingName());
  }

  @Test
  void refreshDropsInvalidOrDeletedBindingsInsteadOfServingStaleCache() throws Exception {
    logAppender = attachLogAppender();
    Path bindingDir = Files.createDirectories(tempDir.resolve("mq-binding"));
    Path deletedBindingFile = bindingDir.resolve("deleted.mq.json");
    Path invalidBindingFile = bindingDir.resolve("invalid.mq.json");
    Files.write(deletedBindingFile, validProducerJson("deleted-binding").getBytes(StandardCharsets.UTF_8));
    Files.write(invalidBindingFile, validProducerJson("invalid-binding").getBytes(StandardCharsets.UTF_8));
    LowCodeMQBindingProvider provider = provider(tempDir);

    List<MQBindingProperties<?>> initialBindings = provider.getAllBindings();

    assertEquals(2, initialBindings.size());

    Files.delete(deletedBindingFile);
    Files.write(invalidBindingFile, "{".getBytes(StandardCharsets.UTF_8));

    refresh(provider);

    List<MQBindingProperties<?>> refreshedBindings = provider.getAllBindings();

    assertTrue(refreshedBindings.isEmpty());
    ILoggingEvent error = assertSingleErrorContaining("invalid.mq.json");
    assertTrue(error.getFormattedMessage().contains("解析消息队列绑定定义"));
    assertTrue(error.getFormattedMessage().contains("失败"));
  }

  @Test
  void proxyDelegatesToCurrentProviderStateAcrossRefresh() throws Exception {
    Path bindingDir = Files.createDirectories(tempDir.resolve("mq-binding"));
    Path bindingFile = bindingDir.resolve("valid.mq.json");
    Files.write(bindingFile, validProducerJson("initial-binding").getBytes(StandardCharsets.UTF_8));
    LowCodeMQBindingProvider provider = provider(tempDir);
    LowCodeMQBindingProvider.Proxy proxy = new LowCodeMQBindingProvider.Proxy();

    List<MQBindingProperties<?>> initialBindings = proxy.getAllBindings();

    assertEquals(1, initialBindings.size());
    assertEquals("initial-binding", initialBindings.get(0).getBindingName());

    Files.write(bindingFile, validProducerJson("refreshed-binding").getBytes(StandardCharsets.UTF_8));

    refresh(provider);

    List<MQBindingProperties<?>> refreshedBindings = proxy.getAllBindings();

    assertEquals(1, refreshedBindings.size());
    assertEquals("refreshed-binding", refreshedBindings.get(0).getBindingName());
  }

  private LowCodeMQBindingProvider provider(Path homePath) {
    LowCodeMQBindingProvider provider = new LowCodeMQBindingProvider();
    provider.appContext = appContext(homePath);
    return provider;
  }

  private AppContext appContext(Path homePath) {
    return () -> homePath.toString();
  }

  private void refresh(LowCodeMQBindingProvider provider) throws Exception {
    Method method = LowCodeMQBindingProvider.class.getDeclaredMethod("refresh");
    method.setAccessible(true);
    method.invoke(provider);
  }

  private CharsetProbeResult runCharsetProbe(String fileEncoding, Path homePath) throws Exception {
    Path outputFile = Files.createTempFile(tempDir, "charset-probe", ".log");
    ProcessBuilder builder = new ProcessBuilder(charsetProbeCommand(fileEncoding, homePath));
    builder.redirectErrorStream(true);
    builder.redirectOutput(outputFile.toFile());

    Process process = builder.start();
    boolean finished = process.waitFor(60, TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
    }

    String output = new String(Files.readAllBytes(outputFile), StandardCharsets.UTF_8);
    assertTrue(finished, output);
    return new CharsetProbeResult(process.exitValue(), output);
  }

  private List<String> charsetProbeCommand(String fileEncoding, Path homePath) {
    List<String> command = new ArrayList<>();
    command.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
    command.add("-Dfile.encoding=" + fileEncoding);
    command.add("-cp");
    command.add(testRuntimeClassPath());
    command.add(LowCodeMQBindingProviderCharsetProbe.class.getName());
    command.add(homePath.toString());
    return command;
  }

  private String testRuntimeClassPath() {
    String classPath = System.getProperty("surefire.test.class.path");
    if (classPath == null || classPath.trim().isEmpty()) {
      classPath = System.getProperty("java.class.path");
    }
    assertNotNull(classPath);
    assertTrue(!classPath.trim().isEmpty());
    return classPath;
  }

  private String validProducerJson(String bindingName) {
    return "{"
        + "\"bindingType\":\"producer\"," 
        + "\"bindingName\":\"" + bindingName + "\"," 
        + "\"destination\":\"orders\""
        + "}";
  }

  private ListAppender<ILoggingEvent> attachLogAppender() {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    return appender;
  }

  private List<ILoggingEvent> errorEvents() {
    return logAppender.list.stream()
        .filter(event -> event.getLevel() == Level.ERROR)
        .collect(Collectors.toList());
  }

  private ILoggingEvent assertSingleErrorContaining(String locationToken) {
    List<ILoggingEvent> errors = errorEvents();
    assertEquals(1, errors.size());
    ILoggingEvent error = errors.get(0);
    assertTrue(error.getFormattedMessage().contains(locationToken));
    return error;
  }

  private static class CharsetProbeResult {
    private final int exitCode;
    private final String output;

    private CharsetProbeResult(int exitCode, String output) {
      this.exitCode = exitCode;
      this.output = output;
    }
  }
}

class LowCodeMQBindingProviderCharsetProbe {

  public static void main(String[] args) throws Exception {
    Logger logger = (Logger) LoggerFactory.getLogger(LowCodeMQBindingProvider.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    boolean previousAdditive = logger.isAdditive();
    logger.setAdditive(false);
    appender.start();
    logger.addAppender(appender);
    try {
      LowCodeMQBindingProvider provider = new LowCodeMQBindingProvider();
      provider.appContext = () -> args[0];
      List<MQBindingProperties<?>> bindings = provider.getAllBindings();
      System.out.println("DEFAULT_CHARSET=" + Charset.defaultCharset().name());
      System.out.println("BINDINGS=" + bindings.size());
      System.out.println("CLASSIFICATION=" + classify(errorEvents(appender)));
    } finally {
      logger.detachAppender(appender);
      logger.setAdditive(previousAdditive);
      appender.stop();
    }
  }

  private static List<ILoggingEvent> errorEvents(ListAppender<ILoggingEvent> appender) {
    return appender.list.stream()
        .filter(event -> event.getLevel() == Level.ERROR)
        .collect(Collectors.toList());
  }

  private static String classify(List<ILoggingEvent> errors) {
    if (errors.isEmpty()) {
      return "NO_ERROR";
    }
    String message = errors.get(0).getFormattedMessage();
    if (message.contains("读取消息队列绑定定义")) {
      return "READ_FAILURE";
    }
    if (message.contains("解析消息队列绑定定义")) {
      return "PARSE_FAILURE";
    }
    if (message.contains("为空")) {
      return "EMPTY_FAILURE";
    }
    return "OTHER_FAILURE";
  }
}

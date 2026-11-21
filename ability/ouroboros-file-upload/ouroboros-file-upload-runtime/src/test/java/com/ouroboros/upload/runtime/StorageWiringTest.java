package com.ouroboros.upload.runtime;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.*;
import com.ouroboros.upload.service.BlobUploadStorageService;
import com.ouroboros.upload.runtime.controller.*;
import static org.junit.jupiter.api.Assertions.*;

class StorageWiringTest {
  @TempDir Path root;
  private AnnotationConfigApplicationContext context(String directory) {
    var context = new AnnotationConfigApplicationContext();
    context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
        "ouroboros.upload.storage.type", "filesystem", "ouroboros.upload.storage.options.root-directory", directory,
        "ouroboros.upload.file-path-template", "", "ouroboros.upload.file-name-template", "{rawName}.{ext}")));
    context.register(FileUploadRuntimeAutoConfiguration.class); context.refresh(); return context;
  }
  @Test void uploadAndDownloadUseTheSameConfiguredModuleStorageAndCloseRequestStream() throws Exception {
    try (var context = context(root.toString())) {
      var closed = new AtomicBoolean();
      var file = new MockMultipartFile("file", "test.txt", "text/plain", "payload".getBytes()) {
        @Override public InputStream getInputStream() {
          return new ByteArrayInputStream("payload".getBytes()) { @Override public void close() throws IOException { closed.set(true); super.close(); } };
        }
      };
      var uploaded = context.getBean(UploadController.class).upload(file, new MockHttpServletRequest());
      assertNotNull(uploaded.getData()); assertTrue(closed.get());
      var response = new MockHttpServletResponse();
      context.getBean(DownloadFileController.class).downloadFile("/test.txt", null, response);
      assertEquals("payload", response.getContentAsString());
      // Failure paths also release request-owned content.
      closed.set(false); context.getBean(UploadController.class).upload(file, new MockHttpServletRequest()); assertTrue(closed.get());
    }
  }
  @Test void identicalConsumerSettingsCreateIndependentInstancesWithIndependentLifecycles() {
    var firstContext = context(root.toString());
    try (var secondContext = context(root.toString())) {
      var first = firstContext.getBean(BlobUploadStorageService.class);
      var second = secondContext.getBean(BlobUploadStorageService.class); assertNotSame(first, second);
      firstContext.close();
      assertTrue(second.addFile(Path.of("alive"), new ByteArrayInputStream(new byte[]{1})).isRight());
    } finally { firstContext.close(); }
  }
}

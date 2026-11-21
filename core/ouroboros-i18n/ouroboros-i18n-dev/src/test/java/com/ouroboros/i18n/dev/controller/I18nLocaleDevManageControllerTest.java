package com.ouroboros.i18n.dev.controller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import io.vavr.control.Try;

import com.ouroboros.i18n.dev.dto.I18nLocaleExportResult;
import com.ouroboros.i18n.dev.dto.I18nLocaleSyncRequest;
import com.ouroboros.i18n.dev.dto.I18nLocaleSyncResult;
import com.ouroboros.i18n.dev.model.I18nLocaleModel;
import com.ouroboros.i18n.dev.service.I18nLocaleDevService;
import com.ouroboros.web.WebApiResult;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class I18nLocaleDevManageControllerTest {

  @Test
  void exportTemplateDownloadsWorkspaceTemplate() {
    TestI18nLocaleDevService service = new TestI18nLocaleDevService();
    service.exportTemplateResult = Try.success(
        new I18nLocaleExportResult("translation-template.json", "{\"locale\":\"template\"}", "application/json")
    );
    I18nLocaleDevManageController controller = new I18nLocaleDevManageController(service);

    ResponseEntity<byte[]> response = controller.exportTemplate("demo-app");

    assertEquals(200, response.getStatusCodeValue());
    assertEquals("application/json", response.getHeaders().getContentType().toString());
    assertTrue(String.valueOf(response.getHeaders().getFirst("Content-Disposition")).contains("translation-template.json"));
    assertArrayEquals("{\"locale\":\"template\"}".getBytes(StandardCharsets.UTF_8), response.getBody());
  }

  @Test
  void exportLocaleDownloadsRequestedLocaleFile() {
    TestI18nLocaleDevService service = new TestI18nLocaleDevService();
    service.exportLocaleResult = Try.success(
        new I18nLocaleExportResult("en-US.i18n.json", "{\"locale\":\"en-US\"}", "application/json")
    );
    I18nLocaleDevManageController controller = new I18nLocaleDevManageController(service);

    ResponseEntity<byte[]> response = controller.exportLocale("demo-app", "en-US");

    assertEquals(200, response.getStatusCodeValue());
    assertTrue(String.valueOf(response.getHeaders().getFirst("Content-Disposition")).contains("en-US.i18n.json"));
    assertArrayEquals("{\"locale\":\"en-US\"}".getBytes(StandardCharsets.UTF_8), response.getBody());
    assertEquals("en-US", service.exportedLocale);
  }

  @Test
  void importLocaleParsesUploadedJsonAndDelegatesToService() {
    TestI18nLocaleDevService service = new TestI18nLocaleDevService();
    service.importLocaleResult = Try.success(new I18nLocaleModel().setLocale("en-US").setName("English"));
    I18nLocaleDevManageController controller = new I18nLocaleDevManageController(service);

    MultipartFile file = new TestMultipartFile(
        "file",
        "en-US.i18n.json",
        "application/json",
        ("{\n"
            + "  \"locale\": \"en-US\",\n"
            + "  \"name\": \"English\",\n"
            + "  \"version\": \"1.0.0\",\n"
            + "  \"messages\": {\n"
            + "    \"common\": {\n"
            + "      \"greeting\": \"Hello\"\n"
            + "    }\n"
            + "  }\n"
            + "}")
            .getBytes(StandardCharsets.UTF_8)
    );

    WebApiResult<I18nLocaleModel> result = controller.importLocale("demo-app", file);

    assertEquals(0, result.getCode());
    assertEquals("en-US", result.getData().getLocale());
    assertEquals("English", result.getData().getName());
    assertEquals("en-US", service.importedModel.getLocale());
    assertEquals("Hello", service.importedModel.getMessages().get("common").get("greeting"));
  }

  @Test
  void syncFromMetadataReturnsSummary() {
    TestI18nLocaleDevService service = new TestI18nLocaleDevService();
    service.syncResult = Try.success(new I18nLocaleSyncResult(2, 1));
    I18nLocaleDevManageController controller = new I18nLocaleDevManageController(service);

    WebApiResult<I18nLocaleSyncResult> result = controller.syncFromMetadata(
        "demo-app",
        "zh-CN",
        new I18nLocaleSyncRequest().setRemoveOrphaned(true)
    );

    assertEquals(0, result.getCode());
    assertEquals(2, result.getData().getAdded());
    assertEquals(1, result.getData().getRemoved());
    assertEquals("zh-CN", service.syncedLocale);
    assertEquals(Boolean.TRUE, service.removeOrphaned);
  }

  private static class TestI18nLocaleDevService extends I18nLocaleDevService {
    private Try<I18nLocaleExportResult> exportTemplateResult = Try.failure(new UnsupportedOperationException());
    private Try<I18nLocaleExportResult> exportLocaleResult = Try.failure(new UnsupportedOperationException());
    private Try<I18nLocaleModel> importLocaleResult = Try.failure(new UnsupportedOperationException());
    private Try<I18nLocaleSyncResult> syncResult = Try.failure(new UnsupportedOperationException());
    private String exportedLocale;
    private I18nLocaleModel importedModel;
    private String syncedLocale;
    private Boolean removeOrphaned;

    @Override
    public Try<I18nLocaleExportResult> exportTemplate() {
      return exportTemplateResult;
    }

    @Override
    public Try<I18nLocaleExportResult> exportLocale(String locale) {
      this.exportedLocale = locale;
      return exportLocaleResult;
    }

    @Override
    public Try<I18nLocaleModel> importLocale(I18nLocaleModel imported) {
      this.importedModel = imported;
      return importLocaleResult;
    }

    @Override
    public Try<I18nLocaleSyncResult> syncFromMetadata(String locale, boolean removeOrphaned) {
      this.syncedLocale = locale;
      this.removeOrphaned = removeOrphaned;
      return syncResult;
    }
  }

  private static class TestMultipartFile implements MultipartFile {
    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final byte[] bytes;

    private TestMultipartFile(String name, String originalFilename, String contentType, byte[] bytes) {
      this.name = name;
      this.originalFilename = originalFilename;
      this.contentType = contentType;
      this.bytes = bytes;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getOriginalFilename() {
      return originalFilename;
    }

    @Override
    public String getContentType() {
      return contentType;
    }

    @Override
    public boolean isEmpty() {
      return bytes.length == 0;
    }

    @Override
    public long getSize() {
      return bytes.length;
    }

    @Override
    public byte[] getBytes() {
      return bytes;
    }

    @Override
    public InputStream getInputStream() {
      return new ByteArrayInputStream(bytes);
    }

    @Override
    public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
      throw new UnsupportedOperationException();
    }
  }
}

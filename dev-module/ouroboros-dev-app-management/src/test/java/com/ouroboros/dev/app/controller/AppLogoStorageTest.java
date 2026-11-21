package com.ouroboros.dev.app.controller;

import java.io.InputStream;
import com.ouroboros.model.AppInfo;
import com.ouroboros.upload.UploadStorageService;
import com.ouroboros.upload.model.TempUploadResult;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.action.VCSAction.BatchAction;
import com.ouroboros.vcs.file.VCSFile;
import io.vavr.control.Either;
import io.vavr.control.Try;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AppLogoStorageTest {
  private final UploadStorageService storage = mock(UploadStorageService.class);
  private final VCSManager vcs = mock(VCSManager.class);
  private final VCSFile metadata = mock(VCSFile.class);
  private final BatchAction batch = mock(BatchAction.class, RETURNS_SELF);
  private final AppSettingController controller = new AppSettingController() {
    @Override protected VCSManager getVcsManager() { return vcs; }
    @Override protected VCSFile getSingleFile() { return metadata; }
  };

  AppLogoStorageTest() {
    ReflectionTestUtils.setField(controller, "uploadStorageService", storage);
  }

  @Test void uploadedValueKeepsTempIdAndClosesRequestStream() {
    var content = new AppExtendJarStorageTest.Content();
    var file = new org.springframework.mock.web.MockMultipartFile("file", "logo.png", "image/png", new byte[0]) {
      @Override public InputStream getInputStream() { return content; }
    };
    var uploaded = new TempUploadResult(); uploaded.setTempFile("content-id");
    when(storage.addTempFile(eq("logo.png"), any(InputStream.class))).thenReturn(Either.right(uploaded));
    var result = controller.uploadLogo("app", file);
    assertEquals(0, result.getCode()); assertEquals("content-id.png", result.getData().getValue());
    assertEquals("content-id", result.getData().getTempFile()); assertTrue(content.closed);
  }

  @ParameterizedTest @CsvSource({"none,false", "none,true", "logo.png,false", "logo.png,true", "logo.jpg,false", "logo.jpg,true"})
  void logoConsumptionUsesTempIdAndClosesContentWhenVcsSucceedsOrFails(String oldLogo, boolean fail) {
    when(vcs.batchOperation()).thenReturn(batch);
    when(metadata.getContent()).thenReturn(oldLogo.equals("none") ? "{}" : "{\"logo\":\"" + oldLogo + "\"}");
    var content = new AppExtendJarStorageTest.Content();
    when(storage.getTempFileContent("content-id")).thenReturn(Either.right(content));
    when(batch.write()).thenAnswer(call -> {
      assertFalse(content.closed); assertEquals("jar-content", new String(content.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
      return fail ? Try.failure(new IllegalStateException("VCS write failed")) : Try.success(true);
    });
    var info = new AppInfo(); info.setLogo("content-id.png");
    var result = controller.updateLogoFile(info);
    assertEquals(fail, result.isFailure()); assertTrue(content.closed);
    if (oldLogo.equals("logo.png")) { verify(batch).update("resources/logo.png", content); }
    else { verify(batch).create("resources/logo.png", content); }
    if (oldLogo.equals("logo.jpg")) { verify(batch).remove("resources/logo.jpg"); }
    verify(storage).getTempFileContent("content-id");
    assertEquals("logo.png", info.getLogo());
  }
}

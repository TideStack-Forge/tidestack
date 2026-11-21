package com.ouroboros.dev.app.controller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.ouroboros.dev.app.service.ExtendJarService;
import com.ouroboros.exception.InternalException;
import com.ouroboros.upload.UploadStorageService;
import com.ouroboros.upload.model.ChunkResult;
import com.ouroboros.upload.model.TempUploadResult;
import com.ouroboros.upload.model.request.FinishChunkRequest;
import io.vavr.control.Either;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AppExtendJarStorageTest {
  private final UploadStorageService storage = mock(UploadStorageService.class);
  private final ExtendJarService jars = mock(ExtendJarService.class);
  private final AppExtendJarController controller = new AppExtendJarController();

  AppExtendJarStorageTest() {
    ReflectionTestUtils.setField(controller, "uploadStorageService", storage);
    ReflectionTestUtils.setField(controller, "extendJarService", jars);
  }

  @ParameterizedTest @ValueSource(booleans = {false, true})
  void completedChunksReadReturnedTempIdAndCloseContent(boolean update) throws IOException {
    var request = finishRequest();
    var completed = new TempUploadResult(); completed.setTempFile("content-id");
    var content = new Content();
    when(storage.finishTempChunk(any(FinishChunkRequest.class))).thenReturn(Either.right(completed));
    when(storage.finishTempChunk(anyString(), anyString(), anyList())).thenReturn(Either.right(completed));
    when(storage.getTempFileContent("content-id")).thenReturn(Either.right(content));
    when(jars.addExtendJar(eq("app"), eq("new.jar"), any(InputStream.class))).thenAnswer(call -> {
      assertArrayEquals("jar-content".getBytes(StandardCharsets.UTF_8), call.getArgument(2, InputStream.class).readAllBytes());
      return Either.right(null);
    });
    when(jars.updateExtendJar(eq("app"), eq("old.jar"), any(InputStream.class))).thenAnswer(call -> {
      assertArrayEquals("jar-content".getBytes(StandardCharsets.UTF_8), call.getArgument(2, InputStream.class).readAllBytes());
      return Either.right(null);
    });
    var result = update ? controller.finishChunk("app", "old.jar", request) : controller.finishChunk("app", request);
    assertEquals(0, result.getCode()); assertEquals("new.jar", result.getData().getValue());
    verify(storage).getTempFileContent("content-id");
    verify(storage, never()).getTempFileContent("session-id");
    assertTrue(content.closed);
  }

  @ParameterizedTest @ValueSource(booleans = {false, true})
  void consumerFailureStillClosesCompletedContent(boolean update) {
    var completed = new TempUploadResult(); completed.setTempFile("content-id");
    var content = new Content();
    when(storage.finishTempChunk(any(FinishChunkRequest.class))).thenReturn(Either.right(completed));
    when(storage.finishTempChunk(anyString(), anyString(), anyList())).thenReturn(Either.right(completed));
    when(storage.getTempFileContent("content-id")).thenReturn(Either.right(content));
    when(jars.addExtendJar(anyString(), anyString(), any(InputStream.class))).thenReturn(Either.left(new InternalException("jar failure")));
    when(jars.updateExtendJar(anyString(), anyString(), any(InputStream.class))).thenReturn(Either.left(new InternalException("jar failure")));
    var result = update ? controller.finishChunk("app", "old.jar", finishRequest()) : controller.finishChunk("app", finishRequest());
    assertNotEquals(0, result.getCode()); assertTrue(content.closed);
  }

  @ParameterizedTest @ValueSource(booleans = {false, true})
  void ordinaryUploadsCloseRequestContent(boolean update) {
    var content = new Content(); var file = file(content);
    when(jars.addExtendJar(anyString(), anyString(), any(InputStream.class))).thenReturn(Either.right(null));
    when(jars.updateExtendJar(anyString(), anyString(), any(InputStream.class))).thenReturn(Either.right(null));
    var result = update ? controller.update("app", "old.jar", file) : controller.add("app", file);
    assertEquals(0, result.getCode()); assertTrue(content.closed);
  }

  @ParameterizedTest @ValueSource(booleans = {false, true})
  void partUploadsCloseRequestContent(boolean update) {
    var content = new Content(); var request = new AppExtendJarController.UploadChunkRequestVO();
    request.setFile(file(content)); request.setUploadId("session-id"); request.setKey("key");
    request.setPartNumber(1); request.setPartSize(11L);
    when(storage.addTempChunk(any())).thenAnswer(call -> {
      assertArrayEquals("jar-content".getBytes(StandardCharsets.UTF_8),
          call.getArgument(0, com.ouroboros.upload.model.request.UploadChunkRequest.class).getPartContent().readAllBytes());
      return Either.right(new ChunkResult());
    });
    var result = update ? controller.uploadChunk("app", "old.jar", request) : controller.uploadChunk(request);
    assertEquals(0, result.getCode()); assertTrue(content.closed);
  }

  @Test void missingCompletedContentDoesNotInvokeJarService() {
    var completed = new TempUploadResult(); completed.setTempFile("content-id");
    when(storage.finishTempChunk(any(FinishChunkRequest.class))).thenReturn(Either.right(completed));
    when(storage.getTempFileContent("content-id")).thenAnswer(call -> Either.left(new InternalException("read failure")));
    assertNotEquals(0, controller.finishChunk("app", finishRequest()).getCode());
    verifyNoInteractions(jars);
  }

  private static FinishChunkRequest finishRequest() {
    var request = new FinishChunkRequest(); request.setUploadId("session-id");
    request.setFileName("new.jar"); request.setPartList(List.of()); return request;
  }
  static MockMultipartFile file(Content content) {
    return new MockMultipartFile("file", "new.jar", "application/octet-stream", new byte[0]) {
      @Override public InputStream getInputStream() { return content; }
    };
  }
  static class Content extends ByteArrayInputStream {
    boolean closed;
    Content() { super("jar-content".getBytes(StandardCharsets.UTF_8)); }
    @Override public void close() throws IOException { closed = true; super.close(); }
  }
}

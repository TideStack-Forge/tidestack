package com.ouroboros.documentmanagement.webapp.controller;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.documentmanagement.model.DocumentNode;
import com.ouroboros.documentmanagement.service.DocumentLibraryContentFacade;
import com.ouroboros.documentmanagement.webapp.util.UserContextHolder;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/document-libraries/{libraryId}")
public class DocumentFileOperationController extends DocumentControllerSupport {

  public DocumentFileOperationController(DocumentLibraryContentFacade facade) {
    super(facade);
  }

  @Operation(
      summary = "预览文件",
      description = "在线预览指定文件并返回文件流。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回预览文件流"),
      @ApiResponse(responseCode = "404", description = "文件不存在或当前用户无权访问")
  })
  @GetMapping("/documents/{documentId}/preview")
  public ResponseEntity<Resource> previewDocument(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "文件 ID", required = true)
      @PathVariable("documentId") String documentId) {
    String userId = UserContextHolder.requireCurrentUserId();
    Optional<InputStream> streamOpt = facade.previewDocument(libraryId, documentId, userId);
    if (!streamOpt.isPresent()) {
      return ResponseEntity.notFound().build();
    }

    Optional<DocumentNode> docOpt = facade.getDocument(libraryId, documentId, userId);
    if (!docOpt.isPresent()) {
      return ResponseEntity.notFound().build();
    }

    DocumentNode document = docOpt.get();
    InputStreamResource resource = new InputStreamResource(streamOpt.get());

    MediaType contentType = document.getMimeType() != null
        ? MediaType.parseMediaType(document.getMimeType())
        : MediaType.APPLICATION_OCTET_STREAM;

    String encodedFilename = encodeRFC5987(document.getName());
    String contentDisposition = String.format(
        "inline; filename=\"%s\"; filename*=UTF-8''%s",
        encodedFilename,
        encodedFilename
    );

    ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
        .contentType(contentType)
        .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition);

    if (document.getSize() != null) {
      builder.contentLength(document.getSize());
    }

    return builder.body(resource);
  }

  @Operation(
      summary = "下载文件",
      description = "下载指定文件并返回附件流。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回下载文件流"),
      @ApiResponse(responseCode = "404", description = "文件不存在或当前用户无权访问")
  })
  @GetMapping("/documents/{documentId}/download")
  public ResponseEntity<Resource> downloadDocument(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "文件 ID", required = true)
      @PathVariable("documentId") String documentId) {
    String userId = UserContextHolder.requireCurrentUserId();
    Optional<InputStream> streamOpt = facade.downloadDocument(libraryId, documentId, userId);
    if (!streamOpt.isPresent()) {
      return ResponseEntity.notFound().build();
    }

    Optional<DocumentNode> docOpt = facade.getDocument(libraryId, documentId, userId);
    if (!docOpt.isPresent()) {
      return ResponseEntity.notFound().build();
    }

    DocumentNode document = docOpt.get();
    InputStreamResource resource = new InputStreamResource(streamOpt.get());

    MediaType contentType = document.getMimeType() != null
        ? MediaType.parseMediaType(document.getMimeType())
        : MediaType.APPLICATION_OCTET_STREAM;

    String encodedFilename = encodeRFC5987(document.getName());
    String contentDisposition = String.format(
        "attachment; filename=\"%s\"; filename*=UTF-8''%s",
        encodedFilename,
        encodedFilename
    );

    ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
        .contentType(contentType)
        .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition);

    if (document.getSize() != null) {
      builder.contentLength(document.getSize());
    }

    return builder.body(resource);
  }

  @Operation(
      summary = "上传文件",
      description = "向指定目录上传一个或多个文件，支持 file 与 file[] 两种表单字段名。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "文件上传成功"),
      @ApiResponse(responseCode = "400", description = "未提供有效上传文件"),
      @ApiResponse(responseCode = "404", description = "目录不存在或当前用户无权访问")
  })
  @PostMapping("/directories/{directoryId}/upload")
  public WebApiResult<List<DocumentNode>> uploadFile(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "目标目录 ID", required = true)
      @PathVariable("directoryId") String directoryId,
      @Parameter(description = "上传文件列表，表单字段名为 file")
      @RequestParam(value = "file", required = false) MultipartFile[] files,
      @Parameter(description = "上传文件列表，表单字段名为 file[]")
      @RequestParam(value = "file[]", required = false) MultipartFile[] filesArray) throws IOException {
    String userId = UserContextHolder.requireCurrentUserId();

    MultipartFile[] actualFiles = (files != null && files.length > 0) ? files : filesArray;
    if (actualFiles == null || actualFiles.length == 0) {
      throw new IllegalArgumentException("未选择要上传的文件");
    }

    List<DocumentNode> uploadedDocuments = new ArrayList<>();
    for (MultipartFile file : actualFiles) {
      if (!file.isEmpty()) {
        DocumentNode document =
            facade.uploadFile(
                libraryId,
                directoryId,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                file.getInputStream(),
                userId);
        uploadedDocuments.add(document);
      }
    }
    return WebApiResult.success(uploadedDocuments);
  }

  private String encodeRFC5987(String filename) {
    try {
      return URLEncoder.encode(filename, StandardCharsets.UTF_8.name()).replace("+", "%20");
    } catch (java.io.UnsupportedEncodingException e) {
      return filename;
    }
  }
}

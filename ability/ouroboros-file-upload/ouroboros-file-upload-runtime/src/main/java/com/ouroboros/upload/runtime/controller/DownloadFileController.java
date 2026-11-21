package com.ouroboros.upload.runtime.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.upload.StorageManagerService;
import com.ouroboros.util.Json;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/file")
public class DownloadFileController {
  private final StorageManagerService storageManagerService;

  public DownloadFileController(@org.springframework.beans.factory.annotation.Qualifier("uploadStorageService") StorageManagerService storageManagerService) {
    this.storageManagerService = storageManagerService;
  }

  @GetMapping("/{*filePath}")
  @PermitAll
  @Operation(
      summary = "下载文件",
      description = "根据存储路径下载运行时上传文件，可选指定下载文件名。",
      tags = {"file-upload"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "文件下载成功"),
      @ApiResponse(responseCode = "404", description = "文件不存在")
  })
  public void downloadFile(
      @Parameter(description = "文件存储路径", required = true)
      @PathVariable String filePath,
      @Parameter(description = "下载文件名")
      @RequestParam(required = false) String fileName,
      HttpServletResponse response) throws IOException {
    var outStream = response.getOutputStream();

    var inStream = storageManagerService.getFileStream(filePath);
    if (inStream.isRight()) {
      // 如果指定了文件名，则设置 Content-Disposition
      if (StringUtils.isNotBlank(fileName)) {
        fileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
      }

      try (var inputStream = inStream.get()) {
        var buffer = new byte[1024];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
          outStream.write(buffer, 0, len);
        }
      }
    } else {
      outStream.write(Json.toJsonString(WebApiResult.error(inStream.getLeft().getMessage())).getBytes());
    }

    outStream.flush();
  }
}

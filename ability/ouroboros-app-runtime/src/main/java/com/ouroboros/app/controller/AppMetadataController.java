package com.ouroboros.app.controller;

import java.nio.file.Paths;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.AppContext;
import com.ouroboros.exception.InternalException;
import com.ouroboros.security.annotation.Anonymous;

@RestController
@RequestMapping("/api/app-metadata")
public class AppMetadataController {
  @Autowired
  AppContext appContext;

  @Operation(
      summary = "获取应用 Logo 文件",
      description = "按文件名读取应用运行目录中的 Logo 资源并直接输出图片内容。",
      tags = {"app-runtime"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回 Logo 文件或空响应"),
      @ApiResponse(responseCode = "500", description = "Logo 文件读取失败")
  })
  @GetMapping("/{logoFileName}")
  @Anonymous
  public void getLogoFile(
      @Parameter(description = "Logo 文件名", required = true)
      @PathVariable String logoFileName,
      HttpServletResponse response) {
    var homePath = appContext.getHomePath();
    var logoFilePath = Paths.get(homePath, "resources", logoFileName);
    var file = logoFilePath.toFile();
    if (file.exists()) {
      response.setContentType("image/png");
      try (var inputStream = file.toURI().toURL().openStream()) {
        IOUtils.copy(inputStream, response.getOutputStream());
      } catch (Exception e) {
        throw new InternalException("Logo 文件读取失败", e);
      }
    }
  }
}

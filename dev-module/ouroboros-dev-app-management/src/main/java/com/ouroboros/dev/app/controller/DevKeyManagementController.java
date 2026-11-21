package com.ouroboros.dev.app.controller;

import java.nio.charset.StandardCharsets;

import jakarta.validation.Valid;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.dev.app.model.DebugKeyGenerationRequest;
import com.ouroboros.dev.app.model.DevKeyMaterialDTO;
import com.ouroboros.dev.app.model.DevKeyPluginConfigDTO;
import com.ouroboros.dev.app.service.runtime.DebugKeyManagementService;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/dev/{appName}/dev-keys")
public class DevKeyManagementController {
  private final DebugKeyManagementService debugKeyManagementService;
  private final ObjectMapper objectMapper;

  public DevKeyManagementController(DebugKeyManagementService debugKeyManagementService, ObjectMapper objectMapper) {
    this.debugKeyManagementService = debugKeyManagementService;
    this.objectMapper = objectMapper;
  }

  @PostMapping
  public WebApiResult<DevKeyMaterialDTO> generate(@PathVariable("appName") String appName,
                                                  @Valid @RequestBody DebugKeyGenerationRequest request) {
    DebugKeyGenerationRequest normalized = request == null ? new DebugKeyGenerationRequest() : request;
    return WebApiResult.success(debugKeyManagementService.generateDevKey(appName, normalized.getDescription(), normalized.getExpiryDays()));
  }

  @GetMapping
  public WebApiResult<DevKeyMaterialDTO> get(@PathVariable("appName") String appName) {
    // 当前用户尚未为该应用生成 Dev Key 属于正常空态，不是请求失败：
    // 弹窗按 ${devKey} 是否为空渲染"当前应用没有开发密钥"引导，返回错误码会让空态被当成失败并弹出错误提示。
    return WebApiResult.success(debugKeyManagementService.getDevKey(appName));
  }

  @PutMapping
  public WebApiResult<DevKeyMaterialDTO> rotate(@PathVariable("appName") String appName,
                                                @Valid @RequestBody DebugKeyGenerationRequest request) {
    DebugKeyGenerationRequest normalized = request == null ? new DebugKeyGenerationRequest() : request;
    return WebApiResult.success(debugKeyManagementService.rotateDevKey(appName, normalized.getDescription(), normalized.getExpiryDays()));
  }

  @DeleteMapping
  public ResponseEntity<Void> delete(@PathVariable("appName") String appName) {
    debugKeyManagementService.deleteDevKey(appName);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/plugin-config")
  public WebApiResult<DevKeyPluginConfigDTO> getPluginConfig(@PathVariable("appName") String appName) {
    return WebApiResult.success(debugKeyManagementService.getDevKeyPluginConfig(appName));
  }

  @GetMapping("/plugin-config/download")
  public ResponseEntity<byte[]> downloadPluginConfig(@PathVariable("appName") String appName) throws JsonProcessingException {
    DevKeyPluginConfigDTO config = debugKeyManagementService.getDevKeyPluginConfig(appName);
    byte[] content = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(config);
    ContentDisposition contentDisposition = ContentDisposition.attachment()
        .filename(downloadFileName(config.getAppName()), StandardCharsets.UTF_8)
        .build();
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .contentLength(content.length)
        .body(content);
  }

  private String downloadFileName(String appName) {
    return appName.replaceAll("[^A-Za-z0-9._-]", "_") + "-tidestack-develop-config.json";
  }
}

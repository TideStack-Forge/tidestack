package com.ouroboros.dev.app.controller;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vavr.control.Try;

import com.ouroboros.dev.runtime.model.DebugSession;
import com.ouroboros.dev.runtime.model.DebugTunnelTargetType;
import com.ouroboros.dev.runtime.model.OpenDebugSessionCommand;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.RuntimeStatus;
import com.ouroboros.dev.runtime.service.DebugSessionService;
import com.ouroboros.dev.runtime.service.RuntimeStateStore;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/dev/{appName}/builtin-role")
public class BuiltinRoleAuthorityDefineTreeController {

  private static final ParameterizedTypeReference<WebApiResult<Object>> RESULT_TYPE = new ParameterizedTypeReference<WebApiResult<Object>>() {
  };

  private final RuntimeStateStore runtimeStateStore;
  private final DebugSessionService debugSessionService;
  private final ObjectMapper objectMapper;

  public BuiltinRoleAuthorityDefineTreeController(RuntimeStateStore runtimeStateStore,
                                                  DebugSessionService debugSessionService,
                                                  ObjectMapper objectMapper) {
    this.runtimeStateStore = runtimeStateStore;
    this.debugSessionService = debugSessionService;
    this.objectMapper = objectMapper;
  }

  @GetMapping("/authority-define-tree")
  public WebApiResult<Object> authorityDefineTree(@PathVariable String appName) {
    return WebApiResult.of(fetchAuthorityDefineTree(appName));
  }

  private Try<Object> fetchAuthorityDefineTree(String appName) {
    HelperRuntime helperRuntime = resolveRunningHelper(appName);
    return debugSessionService.open(openCommand(helperRuntime))
        .flatMap(session -> fetchThroughSession(session)
            .flatMap(data -> closeSession(session.getSessionId()).map(ignore -> data))
            .recoverWith(error -> closeSession(session.getSessionId()).flatMap(ignore -> Try.failure(error))));
  }

  private HelperRuntime resolveRunningHelper(String appName) {
    Map<RuntimeIdentityKey, RuntimeSpec> helperSpecs = runtimeStateStore.allSpecs(RuntimeKind.DEV_HELPER);
    RuntimeIdentityKey runtimeKey = helperSpecs.entrySet().stream()
        .filter(entry -> entry.getValue() != null)
        .filter(entry -> Objects.equals(appName, entry.getValue().getAppName()))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElseThrow(() -> new BusinessException("开发助手未运行，请先启动应用助手后再配置内置角色权限"));
    RuntimeSpec spec = helperSpecs.get(runtimeKey);
    RuntimeInstance instance = runtimeStateStore.allInstances().get(runtimeKey);
    if (instance == null || instance.getStatus() != RuntimeStatus.RUNNING) {
      throw new BusinessException("开发助手未运行，请先启动应用助手后再配置内置角色权限");
    }
    return new HelperRuntime(spec);
  }

  private OpenDebugSessionCommand openCommand(HelperRuntime helperRuntime) {
    String currentUserId = Optional.ofNullable(CurrentSecurityContextHolder.findAuthentication().orElse(null))
        .map(authentication -> authentication.getPrincipal())
        .map(Object::toString)
        .orElse(null);
    return OpenDebugSessionCommand.builder()
        .appName(helperRuntime.spec.getAppName())
        .profile(helperRuntime.spec.getProfile())
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(helperRuntime.spec.getBackendType())
        .targetType(DebugTunnelTargetType.BACKEND_API)
        .ownerUserId(currentUserId)
        .ownerUserName(currentUserId)
        .reason("load builtin role authority tree")
        .maxLifetimeSeconds(Integer.valueOf(60))
        .build();
  }

  private Try<Object> fetchThroughSession(DebugSession session) {
    if (session == null || session.getBinding() == null || StringUtils.isBlank(session.getBinding().getHost()) || session.getBinding().getPort() == null) {
      return Try.failure(new BusinessException("后端 API 会话未建立，无法读取应用内权限树"));
    }
    return Try.of(() -> {
      URL url = new URL("http", session.getBinding().getHost(), session.getBinding().getPort().intValue(), "/api/sys/authority/define-tree");
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      connection.setRequestProperty("Accept", "application/json");
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(10000);
      int status = connection.getResponseCode();
      try (InputStream bodyStream = status >= HttpStatus.BAD_REQUEST.value() ? connection.getErrorStream() : connection.getInputStream()) {
        if (bodyStream == null) {
          throw new BusinessException("目标应用返回空响应，无法读取应用内权限树");
        }
        WebApiResult<Object> result = objectMapper.readValue(bodyStream, objectMapper.getTypeFactory().constructType(RESULT_TYPE.getType()));
        if (status >= HttpStatus.BAD_REQUEST.value()) {
          throw new BusinessException("读取应用内权限树失败: " + firstNonBlank(result == null ? null : result.getMsg(), "HTTP " + status));
        }
        if (result == null) {
          throw new BusinessException("目标应用返回空响应，无法读取应用内权限树");
        }
        if (!Objects.equals(Integer.valueOf(0), result.getCode())) {
          throw new BusinessException(firstNonBlank(result.getMsg(), "读取应用内权限树失败"));
        }
        return result.getData();
      } catch (IOException error) {
        throw new BusinessException("读取应用内权限树失败: " + error.getMessage(), error);
      } finally {
        connection.disconnect();
      }
    });
  }

  private Try<Void> closeSession(String sessionId) {
    return debugSessionService.close(sessionId)
        .recoverWith(error -> Try.failure(new BusinessException("关闭后端 API 会话失败: " + error.getMessage(), error)));
  }

  private String firstNonBlank(String preferred, String fallback) {
    return StringUtils.isNotBlank(preferred) ? preferred : fallback;
  }

  private static final class HelperRuntime {
    private final RuntimeSpec spec;

    private HelperRuntime(RuntimeSpec spec) {
      this.spec = spec;
    }
  }
}

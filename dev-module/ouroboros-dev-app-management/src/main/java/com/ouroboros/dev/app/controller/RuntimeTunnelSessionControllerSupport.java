package com.ouroboros.dev.app.controller;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.dev.app.model.DebugSessionDTO;
import com.ouroboros.dev.app.model.OpenDebugSessionRequest;
import com.ouroboros.dev.app.service.runtime.DebugRuntimePreparationService;
import com.ouroboros.dev.app.service.runtime.DebugSessionViewAssembler;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.DebugTunnelTargetType;
import com.ouroboros.dev.runtime.model.OpenDebugSessionCommand;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.service.DebugSessionService;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.web.WebApiResult;

final class RuntimeTunnelSessionControllerSupport {
  private final DebugSessionService debugSessionService;
  private final DebugSessionViewAssembler debugSessionViewAssembler;
  private final DebugRuntimePreparationService debugRuntimePreparationService;
  private final DebugTunnelTargetType targetType;
  private final String emptyRequestMessage;
  private final String sessionIdPrefix;

  RuntimeTunnelSessionControllerSupport(DebugSessionService debugSessionService,
                                        DebugSessionViewAssembler debugSessionViewAssembler,
                                        DebugRuntimePreparationService debugRuntimePreparationService,
                                        DebugTunnelTargetType targetType,
                                        String emptyRequestMessage,
                                        String sessionIdPrefix) {
    this.debugSessionService = debugSessionService;
    this.debugSessionViewAssembler = debugSessionViewAssembler;
    this.debugRuntimePreparationService = debugRuntimePreparationService;
    this.targetType = targetType;
    this.emptyRequestMessage = emptyRequestMessage;
    this.sessionIdPrefix = sessionIdPrefix;
  }

  WebApiResult<DebugSessionDTO> open(OpenDebugSessionRequest request) {
    OpenDebugSessionCommand command = toCommand(request);
    if (StringUtils.isBlank(request.getUploadBatchId())) {
      return WebApiResult.of(debugSessionService.open(command).map(debugSessionViewAssembler::toDto));
    }
    return WebApiResult.of(debugRuntimePreparationService.prepare(buildPreparationSessionId(), command, request.getUploadBatchId())
        .flatMap(debugSessionService::open)
        .map(debugSessionViewAssembler::toDto));
  }

  WebApiResult<DebugSessionDTO> get(String sessionId) {
    return WebApiResult.of(debugSessionService.get(sessionId).map(debugSessionViewAssembler::toDto));
  }

  WebApiResult<Void> close(String sessionId) {
    return WebApiResult.of(debugSessionService.close(sessionId));
  }

  private OpenDebugSessionCommand toCommand(OpenDebugSessionRequest request) {
    if (request == null) {
      throw new BusinessException(emptyRequestMessage);
    }
    return OpenDebugSessionCommand.builder()
        .appName(requiredText(request.getAppName(), "应用名称不能为空"))
        .profile(requiredText(request.getProfile(), "profile 不能为空"))
        .runtimeKind(parseRuntimeKind(request.getRuntimeKind()))
        .backendType(parseBackendType(request.getBackendType()))
        .targetType(targetType)
        .expectedGeneration(request.getExpectedGeneration())
        .ownerUserId(currentUserId())
        .ownerUserName(currentUserId())
        .maxLifetimeSeconds(request.getMaxLifetimeSeconds())
        .idleTimeoutSeconds(request.getIdleTimeoutSeconds())
        .reason(request.getReason())
        .metadata(request.getMetadata())
        .build();
  }

  private RuntimeKind parseRuntimeKind(String value) {
    if (StringUtils.isBlank(value)) {
      return RuntimeKind.TEST_APP;
    }
    try {
      return RuntimeKind.valueOf(normalize(value));
    } catch (Exception error) {
      throw new BusinessException("不支持的运行时类型: " + value);
    }
  }

  private BackendType parseBackendType(String value) {
    try {
      return BackendType.valueOf(normalize(requiredText(value, "运行时后端不能为空")));
    } catch (Exception error) {
      throw new BusinessException("不支持的运行时后端: " + value);
    }
  }

  private String currentUserId() {
    return CurrentSecurityContextHolder.findAuthentication()
        .map(authentication -> authentication.getPrincipal())
        .orElse(null);
  }

  private String requiredText(String value, String message) {
    return Optional.ofNullable(value)
        .map(String::trim)
        .filter(StringUtils::isNotBlank)
        .orElseThrow(() -> new BusinessException(message));
  }

  private String normalize(String value) {
    return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
  }

  private String buildPreparationSessionId() {
    return sessionIdPrefix + UUID.randomUUID().toString();
  }
}

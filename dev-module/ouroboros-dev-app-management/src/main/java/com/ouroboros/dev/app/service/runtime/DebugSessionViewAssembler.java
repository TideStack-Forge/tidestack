package com.ouroboros.dev.app.service.runtime;

import org.springframework.stereotype.Component;

import com.ouroboros.dev.app.model.DebugSessionDTO;
import com.ouroboros.dev.app.model.RuntimeAccessViewDTO;
import com.ouroboros.dev.runtime.model.DebugAccessStrategy;
import com.ouroboros.dev.runtime.model.DebugSession;
import com.ouroboros.dev.runtime.model.DebugSessionBinding;
import com.ouroboros.dev.runtime.model.RuntimeCapability;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.TransportMode;

@Component
public class DebugSessionViewAssembler {

  public DebugSessionDTO toDto(DebugSession session) {
    if (session == null) {
      return null;
    }
    DebugSessionDTO dto = new DebugSessionDTO();
    dto.setSessionId(session.getSessionId());
    dto.setAppName(session.getRuntimeKey() == null ? null : session.getRuntimeKey().getAppName());
    dto.setProfile(session.getRuntimeKey() == null ? null : session.getRuntimeKey().getProfile());
    dto.setRuntimeKind(session.getRuntimeKey() == null || session.getRuntimeKey().getRuntimeKind() == null
        ? null
        : session.getRuntimeKey().getRuntimeKind().name());
    dto.setBackendType(session.getRuntimeKey() == null || session.getRuntimeKey().getBackendType() == null
        ? null
        : session.getRuntimeKey().getBackendType().name());
    dto.setTargetType(session.getTargetType() == null ? null : session.getTargetType().name());
    dto.setTransportMode(session.getTransportMode() == null ? null : session.getTransportMode().name());
    dto.setRuntimeIdAtOpen(session.getRuntimeIdAtOpen());
    dto.setRuntimeGeneration(session.getRuntimeGeneration());
    dto.setUploadBatchId(session.getUploadBatchId());
    dto.setPreparationSessionId(session.getPreparationSessionId());
    dto.setOwnerUserId(session.getOwnerUserId());
    dto.setOwnerUserName(session.getOwnerUserName());
    dto.setStatus(session.getStatus() == null ? null : session.getStatus().name());
    dto.setDebugAccessStrategy(session.getDebugAccessStrategy() == null ? null : session.getDebugAccessStrategy().name());
    dto.setEndpoint(debugEndpoint(session));
    dto.setReason(session.getReason());
    dto.setRequestedAt(session.getRequestedAt());
    dto.setOpenedAt(session.getOpenedAt());
    dto.setFirstConnectedAt(session.getFirstConnectedAt());
    dto.setLastActiveAt(session.getLastActiveAt());
    dto.setExpiresAt(session.getExpiresAt());
    dto.setClosedAt(session.getClosedAt());
    dto.setCloseReason(session.getCloseReason() == null ? null : session.getCloseReason().name());
    dto.setMetadata(session.getMetadata());
    if (session.getBinding() != null) {
      dto.setTargetType(session.getBinding().getTargetType() == null ? dto.getTargetType() : session.getBinding().getTargetType().name());
      dto.setHost(session.getBinding().getHost());
      dto.setPort(session.getBinding().getPort());
      dto.setBindingScope(session.getBinding().getBindingScope() == null ? null : session.getBinding().getBindingScope().name());
      dto.setTransportType(session.getBinding().getTransportType() == null ? null : session.getBinding().getTransportType().name());
      dto.setClientAccessHint(session.getBinding().getClientAccessHint());
    }
    return dto;
  }

  public RuntimeAccessViewDTO toRuntimeAccess(RuntimeSpec spec, RuntimeInstance instance, DebugSession activeDebugSession, DebugSession activeBackendApiSession) {
    RuntimeAccessViewDTO dto = new RuntimeAccessViewDTO();
    dto.setWebAccess(access(strategyOf(spec), instance == null ? null : instance.getWebEndpoint()));
    dto.setApiAccess(access(strategyOf(spec), instance == null ? null : instance.getApiEndpoint()));
    dto.setDebugCapability(debugCapability(instance, activeDebugSession));
    dto.setBackendApiCapability(backendApiCapability(instance, activeBackendApiSession));
    return dto;
  }

  public RuntimeAccessViewDTO toRuntimeAccess(RuntimeSpec spec, RuntimeInstance instance, DebugSession activeSession) {
    return toRuntimeAccess(spec, instance, activeSession, null);
  }

  public String debugEndpoint(DebugSession session) {
    if (session == null || session.getBinding() == null || session.getBinding().getHost() == null || session.getBinding().getPort() == null) {
      return null;
    }
    return transportScheme(session) + "://" + session.getBinding().getHost() + ":" + session.getBinding().getPort();
  }

  private RuntimeAccessViewDTO.AccessView access(String strategy, String endpoint) {
    RuntimeAccessViewDTO.AccessView accessView = new RuntimeAccessViewDTO.AccessView();
    accessView.setStrategy(strategy);
    accessView.setEndpoint(endpoint);
    return accessView;
  }

  private RuntimeAccessViewDTO.DebugCapabilityView debugCapability(RuntimeInstance instance, DebugSession activeSession) {
    RuntimeCapability capability = instance == null ? RuntimeCapability.builder().build() : instance.getCapabilities();
    boolean enabled = capability.isSupportsDebugSession() || capability.isSupportsDebugPort() || activeSession != null;
    RuntimeAccessViewDTO.DebugCapabilityView debugCapability = new RuntimeAccessViewDTO.DebugCapabilityView();
    debugCapability.setEnabled(Boolean.valueOf(enabled));
    debugCapability.setStrategy(enabled ? DebugAccessStrategy.SESSION_TUNNEL.name() : null);
    debugCapability.setActiveSessionId(activeSession == null ? null : activeSession.getSessionId());
    debugCapability.setSessionOpenable(Boolean.valueOf(enabled && activeSession == null));
    debugCapability.setSessionStatus(activeSession == null || activeSession.getStatus() == null ? null : activeSession.getStatus().name());
    debugCapability.setEndpoint(debugEndpoint(activeSession));
    debugCapability.setClientAccessHint(clientAccessHint(activeSession == null ? null : activeSession.getBinding()));
    debugCapability.setBindingScope(activeSession == null || activeSession.getBinding() == null || activeSession.getBinding().getBindingScope() == null
        ? null
        : activeSession.getBinding().getBindingScope().name());
    debugCapability.setTransportType(bindingTransportType(activeSession == null ? null : activeSession.getBinding()));
    return debugCapability;
  }

  private String strategyOf(RuntimeSpec spec) {
    return spec == null || spec.getExposureStrategy() == null ? null : spec.getExposureStrategy().name();
  }

  private String clientAccessHint(DebugSessionBinding binding) {
    return binding == null ? null : binding.getClientAccessHint();
  }

  private RuntimeAccessViewDTO.BackendApiCapabilityView backendApiCapability(RuntimeInstance instance, DebugSession activeSession) {
    RuntimeCapability capability = instance == null ? RuntimeCapability.builder().build() : instance.getCapabilities();
    boolean enabled = capability.isSupportsDebugSession() || activeSession != null;
    RuntimeAccessViewDTO.BackendApiCapabilityView backendApiCapability = new RuntimeAccessViewDTO.BackendApiCapabilityView();
    backendApiCapability.setEnabled(Boolean.valueOf(enabled));
    backendApiCapability.setStrategy(enabled ? "SESSION_TUNNEL" : null);
    backendApiCapability.setActiveSessionId(activeSession == null ? null : activeSession.getSessionId());
    backendApiCapability.setSessionOpenable(Boolean.valueOf(enabled && activeSession == null));
    backendApiCapability.setSessionStatus(activeSession == null || activeSession.getStatus() == null ? null : activeSession.getStatus().name());
    backendApiCapability.setEndpoint(debugEndpoint(activeSession));
    backendApiCapability.setClientAccessHint(clientAccessHint(activeSession == null ? null : activeSession.getBinding()));
    backendApiCapability.setBindingScope(activeSession == null || activeSession.getBinding() == null || activeSession.getBinding().getBindingScope() == null
        ? null
        : activeSession.getBinding().getBindingScope().name());
    backendApiCapability.setTransportType(bindingTransportType(activeSession == null ? null : activeSession.getBinding()));
    return backendApiCapability;
  }

  private String transportScheme(DebugSession session) {
    TransportMode transportMode = session == null || session.getBinding() == null ? null : session.getBinding().getTransportType();
    if (transportMode == null && session != null) {
      transportMode = session.getTransportMode();
    }
    if (transportMode == TransportMode.HTTP) {
      return "http";
    }
    return "tcp";
  }

  private String bindingTransportType(DebugSessionBinding binding) {
    return binding == null || binding.getTransportType() == null ? null : binding.getTransportType().name();
  }
}

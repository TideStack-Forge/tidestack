package com.ouroboros.dev.app.listener;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ouroboros.dev.app.service.AppDevHelperLogService;
import com.ouroboros.dev.runtime.event.DevRuntimeEvent;
import com.ouroboros.dev.runtime.event.DevRuntimeEventType;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.RuntimeKind;

@ExtendWith(MockitoExtension.class)
class DevRuntimeHelperLogPushBridgeTest {

  @Mock
  private AppDevHelperLogService appDevHelperLogService;

  @Test
  void shouldForwardHelperRuntimeLogSnapshotsToHelperLogService() {
    DevRuntimeHelperLogPushBridge bridge = new DevRuntimeHelperLogPushBridge(appDevHelperLogService);

    bridge.onRuntimeEvent(DevRuntimeEvent.builder()
        .type(DevRuntimeEventType.RUNTIME_LOG_OUTPUT)
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.DOCKER)
        .logLines(Arrays.asList("assistant started", "index ready"))
        .build());

    verify(appDevHelperLogService).appendRuntimeOutput(eq("crm"), eq(Arrays.asList("assistant started", "index ready")));
  }
}

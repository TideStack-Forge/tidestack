package com.ouroboros.dev.app.listener;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ouroboros.dev.app.service.AppDevHelperLogService;
import com.ouroboros.dev.runtime.event.DevRuntimeEvent;
import com.ouroboros.dev.runtime.event.DevRuntimeEventType;
import com.ouroboros.dev.runtime.model.RuntimeKind;

@Component
public class DevRuntimeHelperLogPushBridge {
  private final AppDevHelperLogService appDevHelperLogService;

  public DevRuntimeHelperLogPushBridge(AppDevHelperLogService appDevHelperLogService) {
    this.appDevHelperLogService = appDevHelperLogService;
  }

  @EventListener
  public void onRuntimeEvent(DevRuntimeEvent event) {
    if (event == null
        || event.getType() != DevRuntimeEventType.RUNTIME_LOG_OUTPUT
        || event.getRuntimeKind() != RuntimeKind.DEV_HELPER
        || StringUtils.isBlank(event.getAppName())) {
      return;
    }
    List<String> logLines = event.getLogLines();
    if (logLines == null || logLines.isEmpty()) {
      return;
    }
    appDevHelperLogService.appendRuntimeOutput(event.getAppName(), logLines);
  }
}

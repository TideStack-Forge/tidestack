package com.ouroboros.dev.app.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.dev.app.model.DebugSessionDTO;
import com.ouroboros.dev.app.model.OpenDebugSessionRequest;
import com.ouroboros.dev.app.service.runtime.DebugRuntimePreparationService;
import com.ouroboros.dev.app.service.runtime.DebugSessionViewAssembler;
import com.ouroboros.dev.runtime.model.DebugTunnelTargetType;
import com.ouroboros.dev.runtime.service.DebugSessionService;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/dev/runtime/backend-api-sessions")
public class RuntimeBackendApiSessionController {
  private final RuntimeTunnelSessionControllerSupport support;

  public RuntimeBackendApiSessionController(DebugSessionService debugSessionService,
                                            DebugSessionViewAssembler debugSessionViewAssembler,
                                            DebugRuntimePreparationService debugRuntimePreparationService) {
    this.support = new RuntimeTunnelSessionControllerSupport(
        debugSessionService,
        debugSessionViewAssembler,
        debugRuntimePreparationService,
        DebugTunnelTargetType.BACKEND_API,
        "后端 API 会话请求不能为空",
        "bapi-"
    );
  }

  @PostMapping
  public WebApiResult<DebugSessionDTO> open(@RequestBody OpenDebugSessionRequest request) {
    return support.open(request);
  }

  @GetMapping("/{sessionId}")
  public WebApiResult<DebugSessionDTO> get(@PathVariable String sessionId) {
    return support.get(sessionId);
  }

  @DeleteMapping("/{sessionId}")
  public WebApiResult<Void> close(@PathVariable String sessionId) {
    return support.close(sessionId);
  }
}

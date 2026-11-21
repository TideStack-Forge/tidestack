package com.ouroboros.dev.codeintelligence;

import com.ouroboros.dev.codeintelligence.service.CodeContextService;
import org.springframework.web.bind.annotation.*;
import com.ouroboros.web.WebApiResult;
import io.vavr.control.Try;
@RestController
@RequestMapping("/api/dev/{appName}/code-intelligence")
public final class CodeContextController {
  private final CodeContextService service;
  public CodeContextController(CodeContextService service) { this.service = service; }
  @PostMapping("/context")
  public WebApiResult<CodeContextResponse> describe(@PathVariable("appName") String appName, @RequestBody CodeContextRequest request) {
    return WebApiResult.of(Try.of(() -> service.describe(appName, request)));
  }
}

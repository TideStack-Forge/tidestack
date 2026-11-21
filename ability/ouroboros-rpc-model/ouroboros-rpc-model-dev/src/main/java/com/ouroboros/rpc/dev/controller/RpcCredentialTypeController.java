package com.ouroboros.rpc.dev.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ouroboros.rpc.security.RPCAuthCredentialCenter;
import com.ouroboros.web.WebApiResult;

/** Public type descriptions only; installation contributes both builder and atomic schema. */
@RestController
public class RpcCredentialTypeController {
  @GetMapping("/api/dev/{appName}/rpc-auth/credential-types")
  public WebApiResult<?> types() {
    return WebApiResult.ok(Map.of("options", RPCAuthCredentialCenter.credentialTypes().stream()
        .map(type -> Map.of("label", type.title(), "value", type.name())).toList()));
  }
}

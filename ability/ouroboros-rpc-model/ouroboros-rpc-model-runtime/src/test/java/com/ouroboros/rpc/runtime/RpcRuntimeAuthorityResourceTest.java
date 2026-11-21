package com.ouroboros.rpc.runtime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.util.StreamUtils;

class RpcRuntimeAuthorityResourceTest {
  @Test
  void rpcRuntimeManagementAuthority_includesRepairPolicyAction() throws Exception {
    InputStream inputStream = Thread.currentThread()
        .getContextClassLoader()
        .getResourceAsStream("META-INF/ouroboros/metadata/rpcRuntimeManagement.authority.json");

    assertNotNull(inputStream);
    String resourceText;
    try {
      resourceText = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
    } finally {
      inputStream.close();
    }

    assertTrue(resourceText.contains("POST /api/sys/rpc-runtime/{modelName}/actions/repair-policy"));
  }
}

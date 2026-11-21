package com.ouroboros.rpc.dev.service.testing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import com.ouroboros.rpc.metadata.RPCModelMetadata;
import com.ouroboros.rpc.security.metadata.RPCCredentialMetadata;
import com.ouroboros.util.Json;

record RpcModelTestSnapshot(String appName, String branch, String mainName, Map<String, String> modelContents,
                            Map<String, String> credentialContents, Map<String, String> resources,
                            Map<String, String> dependencies) {
  RpcModelTestSnapshot {
    modelContents = Map.copyOf(modelContents);
    credentialContents = Map.copyOf(credentialContents);
    resources = Map.copyOf(resources);
    dependencies = Map.copyOf(dependencies);
  }

  RPCModelMetadata model(String name) { return Json.toBean(RPCModelMetadata.class, modelContents.get(name)); }
  RPCCredentialMetadata credential(String name) { return Json.toBean(RPCCredentialMetadata.class, credentialContents.get(name)); }

  String revision() {
    return hash(Map.of("app", appName, "branch", branch, "main", mainName, "resources", resources, "dependencies", dependencies));
  }

  String modelHash() {
    var model = new LinkedHashMap<>(Json.toMap(Json.toJsonString(model(mainName))));
    model.putIfAbsent("retry", 3);
    model.putIfAbsent("timeout", 10000);
    return hash(model);
  }

  static String hash(Object value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(Json.toJsonString(normalize(value)).getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException error) {
      throw new IllegalStateException("SHA-256 is unavailable", error);
    }
  }

  private static Object normalize(Object value) {
    if (value instanceof Map<?, ?> map) {
      var sorted = new TreeMap<String, Object>();
      map.forEach((key, item) -> sorted.put(String.valueOf(key), normalize(item)));
      return sorted;
    }
    if (value instanceof java.util.List<?> list) {
      return list.stream().map(RpcModelTestSnapshot::normalize).toList();
    }
    return value;
  }
}

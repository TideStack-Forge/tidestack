package com.ouroboros.security.rbac.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.util.Json;

/**
 * 覆盖内置角色定义经 JSON 往返后 {@code accessControlParams} 的保留情况。
 * <p>该字段随角色定义写入 {@code *.role.json}，若读写过程中丢失，
 * 已配置的数据权限会在下次加载后静默失效。
 */
class BuiltinRoleAuthorityModelJsonRoundTripTest {

  @Test
  void serializedAccessControlParamsShouldSurviveRoundTrip() {
    var authority = new BuiltinRoleAuthorityModel();
    authority.setAuthority("workflow.approve");
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("allowedFields", List.of("*"));
    params.put("disallowedFields", List.of());
    params.put("filters", new LinkedHashMap<String, Object>());
    authority.setAccessControlParams(params);

    var role = new BuiltinRoleModel();
    role.setId("builtin-reviewer");
    role.setName("内置审阅者");
    role.setAuthorities(List.of(authority));

    var json = Json.toPrettyJsonString(role);
    assertTrue(json.contains("accessControlParams"),
        "序列化结果应包含 accessControlParams，实际=" + json);

    var restored = Json.tryToBean(BuiltinRoleModel.class, json).get();
    assertEquals(1, restored.getAuthorityDefinitions().size());
    var restoredParams = restored.getAuthorityDefinitions().get(0).getAccessControlParamsSetting();
    assertEquals(List.of("*"), restoredParams.get("allowedFields"),
        "反序列化后应保留 accessControlParams，实际=" + restoredParams);
  }
}

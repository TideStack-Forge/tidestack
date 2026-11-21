package com.ouroboros.enterprise.token;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.enterprise.ResolvedEnterpriseConnection;

class EnterpriseTokenIdentityTest {
  @Test
  void isolatesProviderConnectionKindRevisionAndExternalSecretWithoutStoringTheSecret() {
    var connection = connection("feishu", "main", "v1");
    String key = EnterpriseTokenIdentity.key(connection, "app", "private-secret");
    assertThat(key).hasSize(64).doesNotContain("private-secret");
    assertThat(key).isEqualTo(EnterpriseTokenIdentity.key(connection, "app", "private-secret"));
    assertThat(key).isNotEqualTo(EnterpriseTokenIdentity.key(connection("wecom", "main", "v1"), "app", "private-secret"));
    assertThat(key).isNotEqualTo(EnterpriseTokenIdentity.key(connection("feishu", "other", "v1"), "app", "private-secret"));
    assertThat(key).isNotEqualTo(EnterpriseTokenIdentity.key(connection("feishu", "main", "v2"), "app", "private-secret"));
    assertThat(key).isNotEqualTo(EnterpriseTokenIdentity.key(connection, "user", "private-secret"));
    assertThat(key).isNotEqualTo(EnterpriseTokenIdentity.key(connection, "app", "replacement"));
    assertThat(EnterpriseTokenIdentity.key(connection, "app", "ab", "c"))
        .isNotEqualTo(EnterpriseTokenIdentity.key(connection, "app", "a", "bc"));
  }

  private ResolvedEnterpriseConnection connection(String provider, String id, String revision) {
    return new ResolvedEnterpriseConnection(id, Map.of("provider", provider, "revision", revision), null);
  }
}

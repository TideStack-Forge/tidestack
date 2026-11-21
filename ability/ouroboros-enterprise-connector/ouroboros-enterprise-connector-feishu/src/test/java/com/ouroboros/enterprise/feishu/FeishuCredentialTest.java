package com.ouroboros.enterprise.feishu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FeishuCredentialTest {
  @Test
  void loginRequiresOnlyApplicationCredentialsAndNeverPrintsThem() {
    var credential = FeishuCredential.fromSecretPayload("{\"appId\":\"cli\",\"appSecret\":\"secret\"}");
    assertThat(credential.verificationToken()).isNull();
    assertThat(credential.encryptKey()).isNull();
    assertThat(credential.applicationIdentity()).containsEntry("appId", "cli");
    assertThat(credential.toString()).doesNotContain("secret", "cli");
    assertThatThrownBy(() -> FeishuCredential.fromSecretPayload("{\"appId\":\"cli\"}"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("appSecret");
  }
}

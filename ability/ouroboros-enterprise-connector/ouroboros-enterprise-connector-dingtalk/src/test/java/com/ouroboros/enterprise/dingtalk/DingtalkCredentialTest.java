package com.ouroboros.enterprise.dingtalk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DingtalkCredentialTest {

  @Test
  void loginCredentialDoesNotRequireBusinessCallbackSecrets() {
    var credential = DingtalkCredential.fromSecretPayload("{\"appKey\":\"app\",\"appSecret\":\"secret\"}");
    assertThat(credential.callbackToken()).isNull();
    assertThat(credential.encodingAesKey()).isNull();
  }

  @Test
  void derivesInternalApplicationFromExclusiveCredentialShape() {
    DingtalkCredential credential = DingtalkCredential.fromSecretPayload("""
        {
          "appKey": "app-key",
          "appSecret": "app-secret",
          "callbackToken": "callback-token",
          "encodingAesKey": "encoding-key"
        }
        """);

    assertThat(credential.isThirdPartyEnterprise()).isFalse();
  }

  @Test
  void derivesThirdPartyApplicationFromExclusiveCredentialShape() {
    DingtalkCredential credential = DingtalkCredential.fromSecretPayload("""
        {
          "suiteKey": "suite-key",
          "suiteSecret": "suite-secret",
          "suiteTicket": "suite-ticket",
          "callbackToken": "callback-token",
          "encodingAesKey": "encoding-key"
        }
        """);

    assertThat(credential.isThirdPartyEnterprise()).isTrue();
  }

  @Test
  void rejectsAmbiguousApplicationCredentialShapes() {
    assertThatThrownBy(() -> DingtalkCredential.fromSecretPayload("""
        {
          "appKey": "app-key",
          "appSecret": "app-secret",
          "suiteKey": "suite-key",
          "suiteSecret": "suite-secret",
          "suiteTicket": "suite-ticket",
          "callbackToken": "callback-token",
          "encodingAesKey": "encoding-key"
        }
        """))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exactly one application credential shape");
  }

  @Test
  void rejectsIncompleteSelectedCredentialShape() {
    assertThatThrownBy(() -> DingtalkCredential.fromSecretPayload("""
        {
          "suiteKey": "suite-key",
          "suiteSecret": "suite-secret",
          "callbackToken": "callback-token",
          "encodingAesKey": "encoding-key"
        }
        """))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("suiteKey/suiteSecret/suiteTicket");
  }
}

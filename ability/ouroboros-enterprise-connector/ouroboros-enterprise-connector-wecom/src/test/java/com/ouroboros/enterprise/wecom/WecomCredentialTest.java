package com.ouroboros.enterprise.wecom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WecomCredentialTest {
  @Test
  void selfBuiltCredentialNeedsNoCallbackFieldsAndHasADistinctApplicationIdentity() {
    var self = WecomCredential.fromSecretPayload("{\"agentId\":\"100001\",\"secret\":\"private\"}");
    assertThat(self.isSelfBuilt()).isTrue();
    assertThat(self.applicationIdentity()).containsEntry("agentId", "100001");
    assertThat(self.callbackToken()).isNull();
    assertThat(self.toString()).doesNotContain("private", "100001");
    var third = WecomCredential.fromSecretPayload(
        "{\"suiteId\":\"suite\",\"suiteSecret\":\"secret\",\"suiteTicket\":\"ticket\",\"permanentCode\":\"code\"}");
    assertThat(third.isSelfBuilt()).isFalse();
    assertThat(third.applicationIdentity()).containsEntry("suiteId", "suite");
    assertThat(third.applicationIdentity()).isNotEqualTo(self.applicationIdentity());
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "null", "[]", "{", "{\"agentId\":\"100001\"}",
      "{\"agentId\":\"abc\",\"secret\":\"secret\"}", "{\"secret\":\"secret\"}",
      "{\"agentId\":\"100001\",\"secret\":\"secret\",\"suiteId\":\"suite\"}",
      "{\"suiteId\":\"suite\",\"suiteSecret\":\"secret\"}"})
  void rejectsIncompleteOrMixedApplicationShapes(String payload) {
    assertThatThrownBy(() -> WecomCredential.fromSecretPayload(payload)).isInstanceOf(IllegalArgumentException.class);
  }
}

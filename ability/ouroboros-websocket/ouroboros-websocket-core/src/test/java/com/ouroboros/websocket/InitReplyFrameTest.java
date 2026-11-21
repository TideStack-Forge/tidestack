package com.ouroboros.websocket;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InitReplyFrameTest {

  @Test
  void shouldDefensivelyCopyHeadersAndExposeUnmodifiableView() {
    Map<String, Object> headers = new LinkedHashMap<String, Object>();
    headers.put("traceId", "trace-1");

    InitReplyFrame frame = new InitReplyFrame("session-1", "/reply", "init", "payload", headers);

    headers.put("tenant", "tenant-1");

    assertThat(frame.getSessionId()).isEqualTo("session-1");
    assertThat(frame.getDestination()).isEqualTo("/reply");
    assertThat(frame.getKind()).isEqualTo("init");
    assertThat(frame.getPayload()).isEqualTo("payload");
    assertThat(frame.getHeaders()).containsEntry("traceId", "trace-1");
    assertThat(frame.getHeaders()).hasSize(1);

    assertThatThrownBy(() -> frame.getHeaders().put("another", "value"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}

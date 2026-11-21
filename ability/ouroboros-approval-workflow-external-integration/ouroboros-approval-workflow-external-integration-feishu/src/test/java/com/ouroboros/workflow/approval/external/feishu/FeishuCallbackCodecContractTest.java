package com.ouroboros.workflow.approval.external.feishu;

import com.ouroboros.enterprise.feishu.FeishuCrypto;

import com.ouroboros.enterprise.feishu.FeishuCredential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.ExternalCallbackRequest;
import com.ouroboros.workflow.approval.external.runtime.ConnectionAwareAdapterResolver;
import com.ouroboros.workflow.approval.external.runtime.event.ExternalCallbackReceiptService;

import io.vavr.control.Try;

class FeishuCallbackCodecContractTest {

  private static final String VERIFICATION_TOKEN = "verification-token";
  private static final String ENCRYPT_KEY = "encrypt-key";

  @Test
  void encryptedEventVerifiesSignatureAndUsesOperateTimeForOrdering() throws Exception {
    FeishuCallbackCodec codec = codec(ENCRYPT_KEY);
    String plain = """
        {
          "schema":"2.0",
          "header":{"event_id":"event-1","event_type":"approval.instance.status_changed_v4",
            "create_time":"1788566300000","token":"verification-token","app_id":"app-id"},
          "event":{"instance_code":"FS-1","status":"APPROVED",
            "operate_time":"1788566400000"}
        }
        """;
    String body = "{\"encrypt\":\"" + encrypt(plain) + "\"}";
    String timestamp = String.valueOf(System.currentTimeMillis() / 1_000L);
    String nonce = "nonce-1";
    String signature = FeishuCrypto.signature(timestamp, nonce, ENCRYPT_KEY, body);
    ExternalCallbackRequest request = new ExternalCallbackRequest(
        FeishuEngineAdapterProvider.ADAPTER_ID, "conn-1", Map.of(
            "X-Lark-Request-Timestamp", timestamp,
            "X-Lark-Request-Nonce", nonce,
            "X-Lark-Signature", signature), body);

    var first = codec.verifyAndParseCallback(request);
    var second = codec.verifyAndParseCallback(request);

    assertThat(first.isSuccess()).isTrue();
    assertThat(second.get()).isEqualTo(first.get());
    assertThat(first.get()).singleElement().satisfies(event -> {
      assertThat(event.eventId()).isEqualTo("event-1");
      assertThat(event.externalInstanceId()).isEqualTo("FS-1");
      assertThat(event.resultCode()).isEqualTo("APPROVED");
      assertThat(event.externalEventTime())
          .isEqualTo(Instant.ofEpochMilli(1_788_566_400_000L).toString());
    });
  }

  @Test
  void taskEventAndUnknownInstanceStatusRemainDistinct() {
    FeishuCallbackCodec codec = codec(null);
    String task = """
        {"schema":"2.0","header":{"event_id":"task-event",
         "event_type":"approval.task.status_changed_v4","token":"verification-token"},
         "event":{"instance_code":"FS-1","task_id":"task-1","status":"ROLLBACK"}}
        """;
    String unknown = """
        {"schema":"2.0","header":{"event_id":"instance-event",
         "event_type":"approval.instance.status_changed_v4","token":"verification-token"},
         "event":{"instance_code":"FS-2","status":"NEW_UNKNOWN"}}
        """;

    var taskResult = codec.verifyAndParseCallback(request(task));
    var unknownResult = codec.verifyAndParseCallback(request(unknown));

    assertThat(taskResult.get()).singleElement().satisfies(event -> {
      assertThat(event.externalTaskId()).isEqualTo("task-1");
      assertThat(event.resultCode()).isEqualTo("FEISHU_TASK_ROLLBACK");
    });
    assertThat(unknownResult.get()).singleElement()
        .extracting(event -> event.resultCode())
        .isEqualTo("FEISHU_STATUS_NEW_UNKNOWN");
  }

  @Test
  void invalidSignatureTokenAndExpiredTimestampAreRejected() throws Exception {
    FeishuCallbackCodec encryptedCodec = codec(ENCRYPT_KEY);
    String plain = """
        {"schema":"2.0","header":{"event_id":"event-1",
         "event_type":"approval.instance.status_changed_v4","token":"verification-token"},
         "event":{"instance_code":"FS-1","status":"APPROVED"}}
        """;
    String body = "{\"encrypt\":\"" + encrypt(plain) + "\"}";
    String expired = String.valueOf(System.currentTimeMillis() / 1_000L - 600L);

    var expiredResult = encryptedCodec.verifyAndParseCallback(new ExternalCallbackRequest(
        FeishuEngineAdapterProvider.ADAPTER_ID, "conn-1", Map.of(
            "X-Lark-Request-Timestamp", expired,
            "X-Lark-Request-Nonce", "nonce",
            "X-Lark-Signature", FeishuCrypto.signature(
                expired, "nonce", ENCRYPT_KEY, body)), body));
    var invalidToken = codec(null).verifyAndParseCallback(request(plain.replace(
        VERIFICATION_TOKEN, "wrong-token")));

    assertThat(expiredResult.isFailure()).isTrue();
    assertThat(invalidToken.isFailure()).isTrue();
  }

  @Test
  void unifiedIngressUsesOriginalReceiptAndRefusesAckUntilDurablyAccepted() throws Exception {
    var resolved = connection(new FeishuCredential("app-id", "app-secret", VERIFICATION_TOKEN, null));
    var catalog = mock(com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog.class);
    when(catalog.resolve("conn-1")).thenReturn(java.util.Optional.of(resolved));
    when(catalog.requireActive("conn-1")).thenReturn(new com.ouroboros.enterprise.EnterpriseConnectionSnapshot(
        "feishu", "conn-1", null, null, null, Map.of(), true, false));
    var receipts = mock(ExternalCallbackReceiptService.class);
    when(receipts.receive(anyString(), anyString(), anyString(), anyString(), anyList()))
        .thenReturn(Try.failure(new com.ouroboros.exception.BusinessException("database unavailable")), Try.success(null));
    var dispatcher = new com.ouroboros.enterprise.runtime.callback.EnterpriseCallbackDispatcher(catalog,
        java.util.List.of(new com.ouroboros.enterprise.feishu.FeishuCallbackProtocol()),
        java.util.List.of(new FeishuApprovalCallbackSubscriber(receipts)));
    String plain = """
        {"schema":"2.0","header":{"event_id":"event-2","event_type":"approval.instance.status_changed_v4","token":"verification-token"},"event":{"instance_code":"FS-2","status":"APPROVED","operate_time":"1788566400000"}}
        """;
    var input = new com.ouroboros.enterprise.callback.EnterpriseCallbackRequest("POST", Map.of(), Map.of(), plain);
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> dispatcher.receive("conn-1", input))
        .hasMessageContaining("未持久受理");
    assertThat(dispatcher.receive("conn-1", input).body()).isNotBlank();
    verify(receipts, org.mockito.Mockito.times(2)).receive(eq(FeishuEngineAdapterProvider.ADAPTER_ID), eq("conn-1"),
        anyString(), anyString(), org.mockito.ArgumentMatchers.argThat(events -> events.size() == 1
            && "APPROVED".equals(events.getFirst().resultCode())));
  }

  private static FeishuCallbackCodec codec(String encryptKey) {
    FeishuCredential credential = new FeishuCredential(
        "app-id", "app-secret", VERIFICATION_TOKEN, encryptKey);
    return new FeishuCallbackCodec(connection(credential), credential);
  }

  private static ResolvedEnterpriseConnection connection(FeishuCredential credential) {
    return new ResolvedEnterpriseConnection("conn-1", Map.of("provider", "feishu"), credential);
  }

  private static ExternalCallbackRequest request(String body) {
    return new ExternalCallbackRequest(
        FeishuEngineAdapterProvider.ADAPTER_ID, "conn-1", Map.of(), body);
  }

  private static String encrypt(String plain) throws Exception {
    byte[] key = MessageDigest.getInstance("SHA-256")
        .digest(ENCRYPT_KEY.getBytes(StandardCharsets.UTF_8));
    byte[] value = plain.getBytes(StandardCharsets.UTF_8);
    int padding = 16 - value.length % 16;
    byte[] padded = Arrays.copyOf(value, value.length + padding);
    Arrays.fill(padded, value.length, padded.length, (byte) padding);
    byte[] iv = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
    byte[] encrypted = cipher.doFinal(padded);
    byte[] framed = Arrays.copyOf(iv, iv.length + encrypted.length);
    System.arraycopy(encrypted, 0, framed, iv.length, encrypted.length);
    return Base64.getEncoder().encodeToString(framed);
  }
}

package com.ouroboros.workflow.approval.external.wecom;

import com.ouroboros.enterprise.wecom.WecomCrypto;

import com.ouroboros.enterprise.wecom.WecomCredential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
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

class WecomCallbackCodecContractTest {

  private static final String SUITE_ID = "suite-1";
  private static final String CALLBACK_TOKEN = "callback-token";
  private static final String ENCODING_AES_KEY = Base64.getEncoder()
      .withoutPadding()
      .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
  private static final WecomCredential CREDENTIAL = new WecomCredential(
      SUITE_ID, "suite-secret", "suite-ticket", "permanent-code",
      CALLBACK_TOKEN, ENCODING_AES_KEY);

  @Test
  void validEncryptedApprovalEventProducesStableCanonicalEnvelope() throws Exception {
    WecomCallbackCodec codec = codec();
    String timestamp = String.valueOf(System.currentTimeMillis() / 1_000L);
    String nonce = "nonce-1";
    String encrypted = encrypt("""
        <xml>
          <Event><![CDATA[sys_approval_change]]></Event>
          <SpNo><![CDATA[WE-1]]></SpNo>
          <SpStatus>2</SpStatus>
          <StatuChangeEvent>10</StatuChangeEvent>
          <ApplyTime>1788566400</ApplyTime>
        </xml>
        """, SUITE_ID);
    String signature = WecomCrypto.sha1Signature(
        CALLBACK_TOKEN, timestamp, nonce, encrypted);
    ExternalCallbackRequest request = request(timestamp, nonce, signature, encrypted);

    var first = codec.verifyAndParseCallback(request);
    var second = codec.verifyAndParseCallback(request);

    assertThat(first.isSuccess()).isTrue();
    assertThat(second.get()).isEqualTo(first.get());
    assertThat(first.get()).singleElement().satisfies(event -> {
      assertThat(event.adapterId()).isEqualTo(WecomEngineAdapterProvider.ADAPTER_ID);
      assertThat(event.connectionId()).isEqualTo("conn-1");
      assertThat(event.externalInstanceId()).isEqualTo("WE-1");
      assertThat(event.resultCode()).isEqualTo("APPROVED");
      assertThat(event.externalEventTime()).isNull();
    });
  }

  @Test
  void expiredSignatureAndWrongSuiteFramingAreRejected() throws Exception {
    WecomCallbackCodec codec = codec();
    String expired = String.valueOf(System.currentTimeMillis() / 1_000L - 600L);
    String nonce = "nonce-2";
    String encrypted = encrypt("""
        <xml><Event>sys_approval_change</Event><SpNo>WE-2</SpNo><SpStatus>3</SpStatus></xml>
        """, SUITE_ID);
    String signature = WecomCrypto.sha1Signature(CALLBACK_TOKEN, expired, nonce, encrypted);
    String now = String.valueOf(System.currentTimeMillis() / 1_000L);
    String wrongFraming = encrypt("""
        <xml><Event>sys_approval_change</Event><SpNo>WE-2</SpNo><SpStatus>3</SpStatus></xml>
        """, "another-suite");
    String wrongSignature = WecomCrypto.sha1Signature(
        CALLBACK_TOKEN, now, nonce, wrongFraming);

    assertThat(codec.verifyAndParseCallback(
        request(expired, nonce, signature, encrypted)).isFailure()).isTrue();
    assertThat(codec.verifyAndParseCallback(
        request(now, nonce, wrongSignature, wrongFraming)).isFailure()).isTrue();
  }

  @Test
  void unknownProviderStatusIsPreservedForDurableManualHandling() throws Exception {
    WecomCallbackCodec codec = codec();
    String timestamp = String.valueOf(System.currentTimeMillis() / 1_000L);
    String encrypted = encrypt("""
        <xml><Event>sys_approval_change</Event><SpNo>WE-3</SpNo><SpStatus>99</SpStatus></xml>
        """, SUITE_ID);
    String signature = WecomCrypto.sha1Signature(
        CALLBACK_TOKEN, timestamp, "nonce-3", encrypted);

    var parsed = codec.verifyAndParseCallback(
        request(timestamp, "nonce-3", signature, encrypted));

    assertThat(parsed.isSuccess()).isTrue();
    assertThat(parsed.get()).singleElement()
        .extracting(event -> event.resultCode())
        .isEqualTo("WECOM_STATUS_99");
  }

  @Test
  void unifiedIngressUsesOriginalReceiptAndRefusesAckUntilDurablyAccepted() throws Exception {
    var resolved = connection();
    var catalog = mock(com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog.class);
    when(catalog.resolve("conn-1")).thenReturn(java.util.Optional.of(resolved));
    when(catalog.requireActive("conn-1")).thenReturn(new com.ouroboros.enterprise.EnterpriseConnectionSnapshot(
        "wecom", "conn-1", null, null, null, Map.of(), true, false));
    var receipts = mock(ExternalCallbackReceiptService.class);
    when(receipts.receive(anyString(), anyString(), anyString(), anyString(), anyList()))
        .thenReturn(Try.failure(new com.ouroboros.exception.BusinessException("database unavailable")), Try.success(null));
    var dispatcher = new com.ouroboros.enterprise.runtime.callback.EnterpriseCallbackDispatcher(catalog,
        java.util.List.of(new com.ouroboros.enterprise.wecom.WecomCallbackProtocol()),
        java.util.List.of(new WecomApprovalCallbackSubscriber(receipts)));
    String plain = """
        <xml><Event>sys_approval_change</Event><SpNo>WE-4</SpNo><SpStatus>2</SpStatus></xml>
        """;
    String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
    String encrypted = encrypt(plain, SUITE_ID);
    String signature = WecomCrypto.sha1Signature(CALLBACK_TOKEN, timestamp, "nonce-4", encrypted);
    var input = new com.ouroboros.enterprise.callback.EnterpriseCallbackRequest("POST", Map.of(), Map.of("msg_signature", signature, "timestamp", timestamp, "nonce", "nonce-4"), "<xml><Encrypt><![CDATA[" + encrypted + "]]></Encrypt></xml>");
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> dispatcher.receive("conn-1", input))
        .hasMessageContaining("未持久受理");
    assertThat(dispatcher.receive("conn-1", input).body()).isNotBlank();
    verify(receipts, org.mockito.Mockito.times(2)).receive(eq(WecomEngineAdapterProvider.ADAPTER_ID), eq("conn-1"),
        anyString(), anyString(), org.mockito.ArgumentMatchers.argThat(events -> events.size() == 1
            && "APPROVED".equals(events.getFirst().resultCode())));
  }

  private static WecomCallbackCodec codec() {
    return new WecomCallbackCodec(connection(), CREDENTIAL);
  }

  private static ResolvedEnterpriseConnection connection() {
    return new ResolvedEnterpriseConnection("conn-1", Map.of("provider", "wecom"), CREDENTIAL);
  }

  private static ExternalCallbackRequest request(
      String timestamp, String nonce, String signature, String encrypted) {
    return new ExternalCallbackRequest(
        WecomEngineAdapterProvider.ADAPTER_ID,
        "conn-1",
        Map.of("msg_signature", signature, "timestamp", timestamp, "nonce", nonce),
        "<xml><Encrypt><![CDATA[" + encrypted + "]]></Encrypt></xml>");
  }

  private static String encrypt(String message, String receiveId) throws Exception {
    byte[] key = Base64.getDecoder().decode(ENCODING_AES_KEY + "=");
    byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
    byte[] receiveIdBytes = receiveId.getBytes(StandardCharsets.UTF_8);
    byte[] framed = ByteBuffer.allocate(20 + messageBytes.length + receiveIdBytes.length)
        .put(new byte[16])
        .putInt(messageBytes.length)
        .put(messageBytes)
        .put(receiveIdBytes)
        .array();
    int padding = 32 - framed.length % 32;
    byte[] padded = Arrays.copyOf(framed, framed.length + padding);
    Arrays.fill(padded, framed.length, padded.length, (byte) padding);
    Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
        new IvParameterSpec(Arrays.copyOfRange(key, 0, 16)));
    return Base64.getEncoder().encodeToString(cipher.doFinal(padded));
  }
}

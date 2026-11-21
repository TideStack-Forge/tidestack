package com.ouroboros.workflow.approval.external.dingtalk;

import com.ouroboros.enterprise.dingtalk.DingtalkCrypto;

import com.ouroboros.enterprise.dingtalk.DingtalkCredential;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.ExternalCallbackEnvelope;
import com.ouroboros.workflow.approval.external.ExternalCallbackRequest;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.exception.BusinessException;

/**
 * M17 钉钉回调编解码测试：官方规则验签/解密往返、篡改、时间窗、check_url、重放、字段映射。
 */
class DingtalkCallbackCodecTest {

  private static final String TOKEN = "testtoken";
  private static final byte[] KEY_BYTES = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
  private static final String ENCODING_AES_KEY = Base64.getEncoder().encodeToString(KEY_BYTES).substring(0, 43);
  private static final DingtalkCredential TEST_CREDENTIAL =
      DingtalkCredential.internalApp("app-key", "app-secret", TOKEN, ENCODING_AES_KEY);

  private DingtalkCallbackCodec codec;

  @BeforeEach
  void setUp() {
    codec = new DingtalkCallbackCodec(new ResolvedEnterpriseConnection(
        "conn-1", Map.of("provider", "dingtalk", "corpId", "corp-1"), TEST_CREDENTIAL), TEST_CREDENTIAL);
  }

  private String now() {
    return String.valueOf(System.currentTimeMillis() / 1000L);
  }

  private ExternalCallbackRequest signedRequest(String plain, String timestamp, String nonce) {
    return signedRequest("conn-1", plain, timestamp, nonce);
  }

  private ExternalCallbackRequest signedRequest(String connectionId, String plain, String timestamp, String nonce) {
    String encrypted = DingtalkCryptoTestSupport.encrypt(ENCODING_AES_KEY, plain);
    String signature = DingtalkCrypto.sha1Signature(TOKEN, timestamp, nonce, encrypted);
    return new ExternalCallbackRequest(
        DingtalkEngineAdapterProvider.ADAPTER_ID, connectionId,
        Map.of("signature", signature, "timestamp", timestamp, "nonce", nonce),
        "{\"encrypt\":\"" + encrypted + "\"}");
  }

  @Test
  void instanceFinishAgreeMapsToApprovedEnvelope() {
    String plain = """
        {"EventType":"bpms_instance_change","processInstanceId":"inst-1","type":"finish",
         "result":"agree","createTime":"2026-08-26 10:00:00","staffId":"ding-1","corpId":"corp-1"}
        """;

    List<ExternalCallbackEnvelope> envelopes =
        codec.verifyAndParseCallback(signedRequest(plain, now(), "nonce-1")).get();

    assertThat(envelopes).hasSize(1);
    ExternalCallbackEnvelope envelope = envelopes.get(0);
    assertThat(envelope.adapterId()).isEqualTo(DingtalkEngineAdapterProvider.ADAPTER_ID);
    assertThat(envelope.externalInstanceId()).isEqualTo("inst-1");
    assertThat(envelope.resultCode()).isEqualTo("APPROVED");
    assertThat(envelope.sequence()).isNull();
  }

  @Test
  void taskFinishRefuseMapsToRejectedWithTaskId() {
    String plain = """
        {"EventType":"bpms_task_change","processInstanceId":"inst-1","taskId":"task-9",
         "type":"finish","result":"refuse","createTime":"2026-08-26 10:01:00"}
        """;

    ExternalCallbackEnvelope envelope =
        codec.verifyAndParseCallback(signedRequest(plain, now(), "nonce-2")).get().get(0);

    assertThat(envelope.externalTaskId()).isEqualTo("task-9");
    assertThat(envelope.eventId()).contains("task-9");
    assertThat(envelope.resultCode()).isEqualTo("REJECTED");
  }

  @Test
  void taskEventIdsIncludeTaskIdToAvoidSameInstanceCollision() {
    String timestamp = now();
    String first = """
        {"EventType":"bpms_task_change","processInstanceId":"inst-1","taskId":"task-1",
         "type":"finish","result":"agree","finishTime":"2026-08-26 10:01:00"}
        """;
    String second = """
        {"EventType":"bpms_task_change","processInstanceId":"inst-1","taskId":"task-2",
         "type":"finish","result":"agree","finishTime":"2026-08-26 10:01:00"}
        """;

    ExternalCallbackEnvelope firstEnvelope =
        codec.verifyAndParseCallback(signedRequest(first, timestamp, "nonce-task-1")).get().get(0);
    ExternalCallbackEnvelope secondEnvelope =
        codec.verifyAndParseCallback(signedRequest(second, timestamp, "nonce-task-2")).get().get(0);

    assertThat(firstEnvelope.eventId()).isNotEqualTo(secondEnvelope.eventId());
    assertThat(firstEnvelope.eventId()).contains("task-1");
    assertThat(secondEnvelope.eventId()).contains("task-2");
  }

  @Test
  void taskEventIdsUsePayloadDigestWhenTaskIdIsMissing() {
    String timestamp = now();
    String first = """
        {"EventType":"bpms_task_change","processInstanceId":"inst-1","staffId":"user-1",
         "type":"finish","result":"agree","finishTime":"2026-08-26 10:01:00"}
        """;
    String second = """
        {"EventType":"bpms_task_change","processInstanceId":"inst-1","staffId":"user-2",
         "type":"finish","result":"agree","finishTime":"2026-08-26 10:01:00"}
        """;

    ExternalCallbackEnvelope firstEnvelope =
        codec.verifyAndParseCallback(signedRequest(first, timestamp, "nonce-task-missing-1")).get().get(0);
    ExternalCallbackEnvelope secondEnvelope =
        codec.verifyAndParseCallback(signedRequest(second, timestamp, "nonce-task-missing-2")).get().get(0);

    assertThat(firstEnvelope.externalTaskId()).isNull();
    assertThat(firstEnvelope.eventId()).isNotEqualTo(secondEnvelope.eventId());
    assertThat(firstEnvelope.payloadDigest()).isNotBlank();
    assertThat(secondEnvelope.payloadDigest()).isNotBlank();
  }

  @Test
  void finishTimeIsUsedAsExternalEventTimeWhenPresent() {
    String plain = """
        {"EventType":"bpms_instance_change","processInstanceId":"inst-1","type":"finish",
         "result":"agree","createTime":"2026-08-26 10:00:00","finishTime":"2026-08-26 10:05:00"}
        """;

    ExternalCallbackEnvelope envelope =
        codec.verifyAndParseCallback(signedRequest(plain, now(), "nonce-finish-time")).get().get(0);

    assertThat(envelope.externalEventTime()).isEqualTo("2026-08-26T02:05:00Z");
  }

  @Test
  void providerLocalTimeIsNormalizedUsingFixedProviderZone() {
    String plain = """
        {"EventType":"bpms_instance_change","processInstanceId":"inst-1","type":"finish",
         "result":"agree","createTime":"2026-08-26 10:00:00"}
        """;
    ResolvedEnterpriseConnection utcConnection = new ResolvedEnterpriseConnection(
        "conn-1", Map.of("provider", "dingtalk", "corpId", "corp-1"), TEST_CREDENTIAL);
    DingtalkCallbackCodec utcCodec = new DingtalkCallbackCodec(utcConnection, TEST_CREDENTIAL);

    ExternalCallbackEnvelope envelope =
        utcCodec.verifyAndParseCallback(signedRequest(plain, now(), "nonce-time-zone")).get().get(0);

    assertThat(envelope.externalEventTime()).isEqualTo("2026-08-26T02:00:00Z");
  }

  @Test
  void instanceTerminateMapsToCanceled() {
    String plain = """
        {"EventType":"bpms_instance_change","processInstanceId":"inst-1","type":"terminate",
         "createTime":"2026-08-26 10:02:00"}
        """;

    ExternalCallbackEnvelope envelope =
        codec.verifyAndParseCallback(signedRequest(plain, now(), "nonce-3")).get().get(0);

    assertThat(envelope.resultCode()).isEqualTo("CANCELED");
  }

  @Test
  void tamperedPayloadFailsVerification() {
    String plain = """
        {"EventType":"bpms_instance_change","processInstanceId":"inst-1","type":"finish","result":"agree"}
        """;
    String encrypted = DingtalkCryptoTestSupport.encrypt(ENCODING_AES_KEY, plain);
    String tampered = encrypted.substring(0, encrypted.length() - 4) + "AAAA";
    String signature = DingtalkCrypto.sha1Signature(TOKEN, now(), "nonce-4", tampered);
    ExternalCallbackRequest request = new ExternalCallbackRequest(
        DingtalkEngineAdapterProvider.ADAPTER_ID, "conn-1",
        Map.of("signature", signature, "timestamp", now(), "nonce", "nonce-4"),
        "{\"encrypt\":\"" + tampered + "\"}");

    var result = codec.verifyAndParseCallback(request);

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause()).isInstanceOf(BusinessException.class);
    assertThat(result.getCause().getMessage())
        .contains(EngineIntegrationErrorCodes.EXTERNAL_EVENT_UNKNOWN)
        .doesNotContain(ENCODING_AES_KEY)
        .doesNotContain("processInstanceId");
  }

  @Test
  void malformedCallbackHeadersUseOuroborosBusinessFailure() {
    ExternalCallbackRequest request = new ExternalCallbackRequest(
        DingtalkEngineAdapterProvider.ADAPTER_ID,
        "conn-1",
        Map.of(),
        "{}");

    var result = codec.verifyAndParseCallback(request);

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause()).isInstanceOf(BusinessException.class);
    assertThat(result.getCause().getMessage())
        .contains(EngineIntegrationErrorCodes.EXTERNAL_EVENT_UNKNOWN);
  }

  @Test
  void staleTimestampFailsTimeWindow() {
    String plain = "{\"EventType\":\"bpms_instance_change\",\"processInstanceId\":\"inst-1\"}";
    long stale = System.currentTimeMillis() / 1000L - 10_000L;

    assertThat(codec.verifyAndParseCallback(
        signedRequest(plain, String.valueOf(stale), "nonce-5")).isFailure()).isTrue();
  }

  @Test
  void checkUrlRequestReturnsEmptyAndIsDetected() {
    String plain = "{\"EventType\":\"check_url\"}";

    var request = signedRequest(plain, now(), "nonce-6");

    assertThat(codec.verifyAndParseCallback(request).get()).isEmpty();
  }

  @Test
  void sameNonceCanBeParsedAgainBeforeReceiptDeduplication() {
    String plain = "{\"EventType\":\"bpms_instance_change\",\"processInstanceId\":\"inst-1\"}";
    ExternalCallbackRequest first = signedRequest(plain, now(), "nonce-7");
    ExternalCallbackRequest replay = signedRequest(plain, now(), "nonce-7");

    assertThat(codec.verifyAndParseCallback(first).isSuccess()).isTrue();
    assertThat(codec.verifyAndParseCallback(replay).isSuccess()).isTrue();
  }

  @Test
  void verifiedPayloadDigestIsStableForSamePayload() {
    String plain = "{\"EventType\":\"bpms_instance_change\",\"processInstanceId\":\"inst-1\"}";
    String timestamp = now();

    var first =
        codec.verifyAndParseCallback(signedRequest(plain, timestamp, "digest-nonce")).get();
    var retry =
        codec.verifyAndParseCallback(signedRequest(plain, timestamp, "digest-nonce")).get();

    assertThat(first.getFirst().payloadDigest()).isEqualTo(retry.getFirst().payloadDigest());
    assertThat(first).hasSize(1);
  }

  @Test
  void callbackConnectionMismatchFails() {
    String plain = "{\"EventType\":\"bpms_instance_change\",\"processInstanceId\":\"inst-1\"}";

    assertThat(codec.verifyAndParseCallback(signedRequest("conn-2", plain, now(), "nonce-mismatch")).isFailure())
        .isTrue();
  }

  @Test
  void batchedPayloadSplitsIntoMultipleEnvelopes() {
    String plain = """
        [{"EventType":"bpms_instance_change","processInstanceId":"inst-1","type":"finish","result":"agree"},
         {"EventType":"bpms_task_change","processInstanceId":"inst-1","taskId":"task-2","type":"finish","result":"refuse"}]
        """;

    List<ExternalCallbackEnvelope> envelopes =
        codec.verifyAndParseCallback(signedRequest(plain, now(), "nonce-batch")).get();

    assertThat(envelopes).hasSize(2);
    assertThat(envelopes.get(0).resultCode()).isEqualTo("APPROVED");
    assertThat(envelopes.get(1).resultCode()).isEqualTo("REJECTED");
    assertThat(envelopes.get(1).externalTaskId()).isEqualTo("task-2");
  }

  @Test
  void receiveIdMismatchFailsVerification() {
    String plain = "{\"EventType\":\"bpms_instance_change\",\"processInstanceId\":\"inst-1\"}";
    String encrypted = DingtalkCryptoTestSupport.encrypt(ENCODING_AES_KEY, plain, "corp-2");
    String signature = DingtalkCrypto.sha1Signature(TOKEN, now(), "nonce-rid", encrypted);
    ExternalCallbackRequest request = new ExternalCallbackRequest(
        DingtalkEngineAdapterProvider.ADAPTER_ID, "conn-1",
        Map.of("signature", signature, "timestamp", now(), "nonce", "nonce-rid"),
        "{\"encrypt\":\"" + encrypted + "\"}");

    assertThat(codec.verifyAndParseCallback(request).isFailure()).isTrue();
  }

  @Test
  void thirdPartyEnterpriseCallbackUsesSuiteKeyAsReceiveKey() {
    DingtalkCredential credential = DingtalkCredential.thirdPartyEnterprise(
        "suite-key", "suite-secret", "suite-ticket", TOKEN, ENCODING_AES_KEY);
    DingtalkCallbackCodec thirdPartyCodec = new DingtalkCallbackCodec(
        new ResolvedEnterpriseConnection(
            "conn-1", Map.of("provider", "dingtalk", "corpId", "corp-1"),
            credential),
        credential);
    String plain = """
        {"EventType":"bpms_instance_change","processInstanceId":"inst-1","type":"finish","result":"agree"}
        """;
    String encrypted = DingtalkCryptoTestSupport.encrypt(ENCODING_AES_KEY, plain, "suite-key");
    String timestamp = now();
    String signature = DingtalkCrypto.sha1Signature(TOKEN, timestamp, "nonce-suite-key", encrypted);
    ExternalCallbackRequest request = new ExternalCallbackRequest(
        DingtalkEngineAdapterProvider.ADAPTER_ID, "conn-1",
        Map.of("signature", signature, "timestamp", timestamp, "nonce", "nonce-suite-key"),
        "{\"encrypt\":\"" + encrypted + "\"}");

    List<ExternalCallbackEnvelope> envelopes = thirdPartyCodec.verifyAndParseCallback(request).get();

    assertThat(envelopes).hasSize(1);
    assertThat(envelopes.get(0).externalInstanceId()).isEqualTo("inst-1");
  }

}

package com.ouroboros.enterprise.feishu;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.enterprise.callback.EnterpriseCallbackRequest;
import com.ouroboros.exception.ValidationException;

class FeishuCallbackProtocolTest {
  private static final String EVENT = """
      {"schema":"2.0","header":{"event_id":"event-1","event_type":"contact.user.updated_v3",
       "token":"token","app_id":"app"},"event":{"user":{"user_id":"alice"}}}
      """;
  private final FeishuCallbackProtocol protocol = new FeishuCallbackProtocol();

  @Test
  void encryptedUrlVerificationReturnsChallengeWithoutRequiringBusinessSignature() throws Exception {
    var result = protocol.verify(connection("encrypt-key"), new EnterpriseCallbackRequest("POST", Map.of(), Map.of(),
        encrypt("{\"type\":\"url_verification\",\"token\":\"token\",\"challenge\":\"challenge-1\"}")));
    assertThat(result.events()).isEmpty();
    assertThat(result.response().body()).isEqualTo("{\"challenge\":\"challenge-1\"}");
  }

  @Test
  void encryptedEventVerifiesSignatureAndDoesNotRequireApprovalFields() throws Exception {
    var request = encryptedRequest(EVENT, now());
    var first = protocol.verify(connection("encrypt-key"), request);
    assertThat(first.events()).singleElement().satisfies(event -> {
      assertThat(event.eventType()).isEqualTo("contact.user.updated_v3");
      assertThat(event.eventId()).isEqualTo("event-1");
      assertThat(event.payload()).isEqualTo(EVENT);
    });
    assertThat(protocol.verify(connection("encrypt-key"), request).events()).isEqualTo(first.events());
    assertThat(first.response().body()).isEqualTo("{\"success\":true}");
  }

  @Test
  void unencryptedSubscriptionStillRequiresVerificationTokenAndAppIdentity() {
    assertThat(protocol.verify(connection(null), plain(EVENT)).events()).hasSize(1);
    assertThatThrownBy(() -> protocol.verify(connection(null), plain(EVENT.replace("\"token\":\"token\"", "\"token\":\"wrong\""))))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> protocol.verify(connection(null), plain(EVENT.replace("\"app_id\":\"app\"", "\"app_id\":\"other\""))))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void invalidSignatureExpiredOrOverflowTimestampAndMissingEncryptedBodyAreRejected() throws Exception {
    var input = encryptedRequest(EVENT, now());
    var headers = new java.util.HashMap<>(input.headers()); headers.put("X-Lark-Signature", "bad");
    assertThatThrownBy(() -> protocol.verify(connection("encrypt-key"),
        new EnterpriseCallbackRequest("POST", headers, Map.of(), input.body()))).isInstanceOf(ValidationException.class);
    for (String time : java.util.List.of("1", String.valueOf(Long.MIN_VALUE), String.valueOf(Long.MAX_VALUE))) {
      assertThatThrownBy(() -> protocol.verify(connection("encrypt-key"), encryptedRequest(EVENT, time)))
          .isInstanceOf(ValidationException.class);
    }
    assertThatThrownBy(() -> protocol.verify(connection("encrypt-key"), plain(EVENT))).isInstanceOf(ValidationException.class);
  }

  @Test
  void unsupportedSynchronousCardCommandCannotBeAcknowledgedAsUnknownAsyncEvent() {
    assertThatThrownBy(() -> protocol.verify(connection(null), plain(EVENT.replace("contact.user.updated_v3", "card.action.trigger"))))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void legacyEventUuidHasStableIdentityAndChallengeRequiresItsDeclaredType() {
    var legacy = plain("{\"token\":\"token\",\"uuid\":\"uuid-1\",\"event\":{\"type\":\"legacy.event\"}}");
    assertThat(protocol.verify(connection(null), legacy).events().getFirst().eventId()).isEqualTo("uuid-1");
    assertThatThrownBy(() -> protocol.verify(connection(null), plain("{\"token\":\"token\",\"challenge\":\"injected\"}")))
        .isInstanceOf(ValidationException.class);
  }

  private static ResolvedEnterpriseConnection connection(String encryptKey) {
    return new ResolvedEnterpriseConnection("main", Map.of("provider", "feishu"), new FeishuCredential("app", "secret", "token", encryptKey));
  }
  private static EnterpriseCallbackRequest plain(String body) { return new EnterpriseCallbackRequest("POST", Map.of(), Map.of(), body); }
  private static String now() { return String.valueOf(System.currentTimeMillis() / 1000); }
  private static EnterpriseCallbackRequest encryptedRequest(String event, String timestamp) throws Exception {
    String body = encrypt(event);
    return new EnterpriseCallbackRequest("POST", Map.of("X-Lark-Request-Timestamp", timestamp,
        "X-Lark-Request-Nonce", "nonce", "X-Lark-Signature", FeishuCrypto.signature(timestamp, "nonce", "encrypt-key", body)), Map.of(), body);
  }
  private static String encrypt(String value) throws Exception {
    byte[] key = MessageDigest.getInstance("SHA-256").digest("encrypt-key".getBytes(StandardCharsets.UTF_8));
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    int padding = 16 - bytes.length % 16;
    byte[] padded = Arrays.copyOf(bytes, bytes.length + padding);
    Arrays.fill(padded, bytes.length, padded.length, (byte) padding);
    byte[] iv = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
    byte[] encrypted = cipher.doFinal(padded);
    byte[] result = Arrays.copyOf(iv, iv.length + encrypted.length);
    System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
    return "{\"encrypt\":\"" + Base64.getEncoder().encodeToString(result) + "\"}";
  }
}

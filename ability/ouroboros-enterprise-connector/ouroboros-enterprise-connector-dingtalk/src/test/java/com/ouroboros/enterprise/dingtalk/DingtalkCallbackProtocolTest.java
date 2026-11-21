package com.ouroboros.enterprise.dingtalk;

import static org.assertj.core.api.Assertions.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.enterprise.callback.EnterpriseCallbackRequest;
import com.ouroboros.exception.ValidationException;

class DingtalkCallbackProtocolTest {
  private static final byte[] KEY = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
  private static final String ENCODING_KEY = Base64.getEncoder().withoutPadding().encodeToString(KEY);
  private static final DingtalkCredential CREDENTIAL = DingtalkCredential.internalApp("app", "secret", "token", ENCODING_KEY);
  private final DingtalkCallbackProtocol protocol = new DingtalkCallbackProtocol();

  @Test
  void acceptsOfficial32BytePaddingFixtureAndReturnsSignedEncryptedSuccess() throws Exception {
    String payload = "{\"EventType\":\"user_add_org\",\"CorpId\":\"corp\"}";
    var result = protocol.verify(connection("CORPORATE"), request(payload, "corp", now(), "POST"));
    assertThat(result.events()).singleElement().satisfies(event -> {
      assertThat(event.eventType()).isEqualTo("user_add_org");
      assertThat(event.payload()).isEqualTo(payload);
      assertThat(event.connectionId()).isEqualTo("main");
    });
    var ack = new ObjectMapper().readTree(result.response().body());
    assertThat(ack.fieldNames()).toIterable().containsExactlyInAnyOrder("msg_signature", "encrypt", "timeStamp", "nonce");
    assertThat(ack.path("msg_signature").asText()).isEqualTo(DingtalkCrypto.sha1Signature("token",
        ack.path("timeStamp").asText(), ack.path("nonce").asText(), ack.path("encrypt").asText()));
    assertThat(decodeFixture(ack.path("encrypt").asText(), "corp")).isEqualTo("success");
  }

  @Test
  void applicationModeUsesAppKeyAndDoesNotAcceptCorporateFraming() throws Exception {
    var application = connection("APPLICATION");
    assertThat(protocol.verify(application, request("{\"EventType\":\"check_url\"}", "app", now(), "POST")).events()).isEmpty();
    assertThatThrownBy(() -> protocol.verify(application,
        request("{\"EventType\":\"check_url\"}", "corp", now(), "POST"))).isInstanceOf(ValidationException.class);
  }

  @Test
  void getChallengeIsDecryptedAndPostCheckUrlProducesNoBusinessEvent() throws Exception {
    assertThat(protocol.verify(connection("CORPORATE"), request("echo", "corp", now(), "GET")).response().body()).isEqualTo("echo");
    assertThat(protocol.verify(connection("CORPORATE"), request("{\"EventType\":\"check_url\"}", "corp", now(), "POST")).events()).isEmpty();
  }

  @Test
  void batchHasStableIndividualIdentitiesAcrossRetries() throws Exception {
    var request = request("[{\"EventType\":\"one\"},{\"EventType\":\"two\"}]", "corp", now(), "POST");
    var first = protocol.verify(connection("CORPORATE"), request).events();
    assertThat(first).hasSize(2);
    assertThat(first.getFirst().eventId()).isNotEqualTo(first.getLast().eventId());
    assertThat(protocol.verify(connection("CORPORATE"), request).events()).isEqualTo(first);
  }

  @Test
  void invalidSignatureAmbiguousSignatureAndExpiredOrOverflowTimeAreRejected() throws Exception {
    var input = request("{\"EventType\":\"one\"}", "corp", now(), "POST");
    var bad = new java.util.HashMap<>(input.parameters());
    bad.put("signature", "different");
    assertThatThrownBy(() -> protocol.verify(connection("CORPORATE"),
        new EnterpriseCallbackRequest("POST", Map.of(), bad, input.body()))).isInstanceOf(ValidationException.class);
    for (String time : java.util.List.of("1", String.valueOf(Long.MIN_VALUE), String.valueOf(Long.MAX_VALUE))) {
      assertThatThrownBy(() -> protocol.verify(connection("CORPORATE"),
          request("{\"EventType\":\"one\"}", "corp", time, "POST"))).isInstanceOf(ValidationException.class);
    }
    bad.remove("signature"); bad.put("msg_signature", "bad");
    assertThatThrownBy(() -> protocol.verify(connection("CORPORATE"),
        new EnterpriseCallbackRequest("POST", Map.of(), bad, input.body()))).isInstanceOf(ValidationException.class);
  }

  @Test
  void millisecondTimestampIsAcceptedButWrongEnterpriseAndMissingMaterialAreRejected() throws Exception {
    assertThat(protocol.verify(connection("CORPORATE"), request("{\"EventType\":\"one\"}", "corp",
        String.valueOf(System.currentTimeMillis()), "POST")).events()).hasSize(1);
    assertThatThrownBy(() -> protocol.verify(connection("CORPORATE"),
        request("{\"EventType\":\"one\",\"corpId\":\"other\"}", "corp", now(), "POST"))).isInstanceOf(ValidationException.class);
    var loginOnly = new ResolvedEnterpriseConnection("main", Map.of("provider", "dingtalk", "corpId", "corp"),
        DingtalkCredential.internalApp("app", "secret", null, null));
    assertThatThrownBy(() -> protocol.verify(loginOnly, request("{}", "corp", now(), "POST")))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void invalidKeyPaddingAndFramingLengthAreRejected() throws Exception {
    assertThatThrownBy(() -> DingtalkCrypto.encrypt(Base64.getEncoder().encodeToString(new byte[16]), "hello", "corp"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("32");
    byte[] invalid = new byte[32];
    ByteBuffer.wrap(invalid, 16, 4).putInt(-1);
    invalid[31] = 1;
    assertThatThrownBy(() -> DingtalkCrypto.decrypt(ENCODING_KEY, encryptBytes(invalid), "corp"))
        .isInstanceOf(IllegalStateException.class);
    invalid[31] = 33;
    assertThatThrownBy(() -> DingtalkCrypto.decrypt(ENCODING_KEY, encryptBytes(invalid), "corp"))
        .isInstanceOf(IllegalStateException.class);
  }

  private static ResolvedEnterpriseConnection connection(String mode) {
    return new ResolvedEnterpriseConnection("main", Map.of("provider", "dingtalk", "corpId", "corp", "callbackMode", mode), CREDENTIAL);
  }
  private static String now() { return String.valueOf(System.currentTimeMillis() / 1000); }
  private static EnterpriseCallbackRequest request(String payload, String owner, String timestamp, String method) throws Exception {
    String encrypted = fixture(payload, owner);
    String signature = DingtalkCrypto.sha1Signature("token", timestamp, "nonce", encrypted);
    return new EnterpriseCallbackRequest(method, Map.of(), "GET".equals(method)
        ? Map.of("msg_signature", signature, "timestamp", timestamp, "nonce", "nonce", "echostr", encrypted)
        : Map.of("msg_signature", signature, "timestamp", timestamp, "nonce", "nonce"),
        "{\"encrypt\":\"" + encrypted + "\"}");
  }
  private static String fixture(String message, String owner) throws Exception {
    byte[] value = message.getBytes(StandardCharsets.UTF_8);
    byte[] recipient = owner.getBytes(StandardCharsets.UTF_8);
    byte[] frame = ByteBuffer.allocate(20 + value.length + recipient.length).put(new byte[16]).putInt(value.length).put(value).put(recipient).array();
    int padding = 32 - frame.length % 32;
    byte[] padded = Arrays.copyOf(frame, frame.length + padding);
    Arrays.fill(padded, frame.length, padded.length, (byte) padding);
    return encryptBytes(padded);
  }
  private static String encryptBytes(byte[] padded) throws Exception {
    Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY, "AES"), new IvParameterSpec(Arrays.copyOf(KEY, 16)));
    return Base64.getEncoder().encodeToString(cipher.doFinal(padded));
  }
  private static String decodeFixture(String encrypted, String owner) throws Exception {
    Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
    cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY, "AES"), new IvParameterSpec(Arrays.copyOf(KEY, 16)));
    byte[] bytes = cipher.doFinal(Base64.getDecoder().decode(encrypted));
    int pad = Byte.toUnsignedInt(bytes[bytes.length - 1]);
    assertThat(pad).isBetween(1, 32);
    for (int i = bytes.length - pad; i < bytes.length; i++) { assertThat(Byte.toUnsignedInt(bytes[i])).isEqualTo(pad); }
    int size = ByteBuffer.wrap(bytes, 16, 4).getInt();
    assertThat(new String(bytes, 20 + size, bytes.length - pad - 20 - size, StandardCharsets.UTF_8)).isEqualTo(owner);
    return new String(bytes, 20, size, StandardCharsets.UTF_8);
  }
}

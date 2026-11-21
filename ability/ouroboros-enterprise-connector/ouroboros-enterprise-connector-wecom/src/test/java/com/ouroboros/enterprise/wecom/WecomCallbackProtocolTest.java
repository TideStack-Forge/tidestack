package com.ouroboros.enterprise.wecom;

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
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.enterprise.callback.EnterpriseCallbackRequest;
import com.ouroboros.exception.ValidationException;

class WecomCallbackProtocolTest {
  private static final byte[] KEY = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
  private static final String ENCODING_KEY = Base64.getEncoder().withoutPadding().encodeToString(KEY);
  private static final WecomCredential CREDENTIAL = WecomCredential.selfBuilt("100001", "secret", "token", ENCODING_KEY);
  private final WecomCallbackProtocol protocol = new WecomCallbackProtocol();

  @Test
  void selfBuiltGetDecryptsChallengeAndWrongCorporateFramingFails() throws Exception {
    assertThat(protocol.verify(connection(CREDENTIAL), request("challenge", "corp", now(), "GET")).response().body()).isEqualTo("challenge");
    assertThatThrownBy(() -> protocol.verify(connection(CREDENTIAL), request("challenge", "other", now(), "GET")))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void directoryEventRequiresNoApprovalFieldsAndHasStableReplayIdentity() throws Exception {
    var input = request("<xml><Event>change_contact</Event><CreateTime>1788566400</CreateTime><UserID>alice</UserID></xml>", "corp", now(), "POST");
    var first = protocol.verify(connection(CREDENTIAL), input);
    assertThat(first.events()).singleElement().satisfies(event -> {
      assertThat(event.eventType()).isEqualTo("change_contact");
      assertThat(event.eventTime()).isEqualTo("1788566400");
      assertThat(event.payload()).contains("alice");
    });
    assertThat(protocol.verify(connection(CREDENTIAL), input).events()).isEqualTo(first.events());
    assertThat(first.response().body()).isEqualTo("success");
  }

  @Test
  void messageIdIsUsedWhenPresentAndRecipientAndAgentAreValidated() throws Exception {
    String payload = "<xml><ToUserName>corp</ToUserName><AgentID>100001</AgentID><Event>changed</Event><MsgId>42</MsgId></xml>";
    assertThat(protocol.verify(connection(CREDENTIAL), request(payload, "corp", now(), "POST")).events().getFirst().eventId()).isEqualTo("42");
    for (String invalid : java.util.List.of(payload.replace("<ToUserName>corp", "<ToUserName>other"), payload.replace("100001", "200002"))) {
      assertThatThrownBy(() -> protocol.verify(connection(CREDENTIAL), request(invalid, "corp", now(), "POST")))
          .isInstanceOf(ValidationException.class);
    }
  }

  @Test
  void thirdPartySuiteFramingRemainsSeparateFromSelfBuiltFraming() throws Exception {
    var suite = new WecomCredential("suite", "secret", "ticket", "permanent", "token", ENCODING_KEY);
    assertThat(protocol.verify(connection(suite), request("echo", "suite", now(), "GET")).response().body()).isEqualTo("echo");
    assertThatThrownBy(() -> protocol.verify(connection(suite), request("echo", "corp", now(), "GET")))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void invalidSignatureMissingMaterialAndExpiredTimestampAreRejected() throws Exception {
    var input = request("<xml><Event>changed</Event></xml>", "corp", now(), "POST");
    var bad = new java.util.HashMap<>(input.parameters()); bad.put("msg_signature", "bad");
    assertThatThrownBy(() -> protocol.verify(connection(CREDENTIAL), new EnterpriseCallbackRequest("POST", Map.of(), bad, input.body())))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> protocol.verify(connection(CREDENTIAL), request("echo", "corp", "1", "GET")))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> protocol.verify(connection(WecomCredential.selfBuilt("100001", "secret", null, null)), input))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void doctypeAndExternalEntitiesAreRejectedEvenInValidEncryptedPayload() throws Exception {
    assertThatThrownBy(() -> protocol.verify(connection(CREDENTIAL), request(
        "<!DOCTYPE xml [<!ENTITY file SYSTEM 'file:///etc/passwd'>]><xml><Event>&file;</Event></xml>", "corp", now(), "POST")))
        .isInstanceOf(ValidationException.class);
  }

  private static ResolvedEnterpriseConnection connection(WecomCredential credential) {
    return new ResolvedEnterpriseConnection("main", Map.of("provider", "wecom", "corpId", "corp"), credential);
  }
  private static String now() { return String.valueOf(System.currentTimeMillis() / 1000); }
  private static EnterpriseCallbackRequest request(String payload, String owner, String timestamp, String method) throws Exception {
    byte[] message = payload.getBytes(StandardCharsets.UTF_8);
    byte[] recipient = owner.getBytes(StandardCharsets.UTF_8);
    byte[] framed = ByteBuffer.allocate(20 + message.length + recipient.length).put(new byte[16]).putInt(message.length).put(message).put(recipient).array();
    int padding = 32 - framed.length % 32;
    byte[] padded = Arrays.copyOf(framed, framed.length + padding);
    Arrays.fill(padded, framed.length, padded.length, (byte) padding);
    Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY, "AES"), new IvParameterSpec(Arrays.copyOf(KEY, 16)));
    String encrypted = Base64.getEncoder().encodeToString(cipher.doFinal(padded));
    String signature = WecomCrypto.sha1Signature("token", timestamp, "nonce", encrypted);
    return new EnterpriseCallbackRequest(method, Map.of(), "GET".equals(method)
        ? Map.of("msg_signature", signature, "timestamp", timestamp, "nonce", "nonce", "echostr", encrypted)
        : Map.of("msg_signature", signature, "timestamp", timestamp, "nonce", "nonce"),
        "<xml><Encrypt><![CDATA[" + encrypted + "]]></Encrypt></xml>");
  }
}

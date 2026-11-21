package com.ouroboros.security.profile;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.InternalException;
import com.ouroboros.security.externalauth.secret.CompositeSecretResolver;

@Component
public final class DeploymentContactVerificationProof implements ContactVerificationProof {
  private final CompositeSecretResolver secrets;
  private final String reference;

  public DeploymentContactVerificationProof(CompositeSecretResolver secrets,
      @Value("${ouroboros.security.profile.verification-key-ref:}") String reference) {
    this.secrets = secrets;
    this.reference = reference;
  }

  private byte[] key() {
    var value = secrets.resolve(reference).orElse(null);
    if (value == null) { return null; }
    try {
      byte[] decoded = Base64.getUrlDecoder().decode(value);
      return decoded.length >= 32 ? decoded : null;
    } catch (IllegalArgumentException error) { return null; }
  }

  public boolean ready() { return key() != null; }

  public String digest(String requestId, String proof) {
    byte[] key = key();
    if (key == null) { throw new BusinessException(503, "[CONTACT_DELIVERY_UNAVAILABLE] 联系方式验证密钥尚未就绪"); }
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal((requestId + ":" + proof).getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException error) { throw new InternalException("无法生成验证摘要", error); }
  }
}

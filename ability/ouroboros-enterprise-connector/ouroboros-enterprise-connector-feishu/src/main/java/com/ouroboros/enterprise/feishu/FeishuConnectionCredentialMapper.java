package com.ouroboros.enterprise.feishu;

import com.ouroboros.enterprise.EnterpriseCredential;
import com.ouroboros.enterprise.EnterpriseConnectionCredentialMapper;

/** Maps a secure credentialRef payload to the Feishu credential type. */
public class FeishuConnectionCredentialMapper implements EnterpriseConnectionCredentialMapper {

  @Override
  public String provider() {
    return "feishu";
  }

  @Override
  public EnterpriseCredential map(String secretPayload) {
    return FeishuCredential.fromSecretPayload(secretPayload);
  }
}

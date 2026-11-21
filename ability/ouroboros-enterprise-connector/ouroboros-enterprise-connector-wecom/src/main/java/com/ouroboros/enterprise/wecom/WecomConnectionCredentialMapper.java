package com.ouroboros.enterprise.wecom;

import com.ouroboros.enterprise.EnterpriseCredential;
import com.ouroboros.enterprise.EnterpriseConnectionCredentialMapper;

/** Maps a secure credentialRef payload to the WeCom credential type. */
public class WecomConnectionCredentialMapper implements EnterpriseConnectionCredentialMapper {

  @Override
  public String provider() {
    return "wecom";
  }

  @Override
  public EnterpriseCredential map(String secretPayload) {
    return WecomCredential.fromSecretPayload(secretPayload);
  }
}

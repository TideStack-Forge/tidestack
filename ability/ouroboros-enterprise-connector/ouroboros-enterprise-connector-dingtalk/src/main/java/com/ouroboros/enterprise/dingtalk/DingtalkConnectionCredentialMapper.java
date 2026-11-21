package com.ouroboros.enterprise.dingtalk;

import com.ouroboros.enterprise.EnterpriseCredential;
import com.ouroboros.enterprise.EnterpriseConnectionCredentialMapper;

/**
 * Maps a DingTalk credentialRef payload into the credential consumed by the adapter.
 */
public class DingtalkConnectionCredentialMapper implements EnterpriseConnectionCredentialMapper {

  @Override
  public String provider() {
    return "dingtalk";
  }

  @Override
  public EnterpriseCredential map(String secretPayload) {
    return DingtalkCredential.fromSecretPayload(secretPayload);
  }
}

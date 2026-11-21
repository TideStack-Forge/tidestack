package com.ouroboros.enterprise.dingtalk;

import com.ouroboros.security.externalauth.config.ProviderSpec;

/** Connection-scoped configuration for DingTalk's provider-specific OAuth bridge. */
public final class DingtalkAuthBridgeProviderSpec extends ProviderSpec {

  private String credentialRef;
  private String corpId;

  public String getCredentialRef() {
    return credentialRef;
  }

  public void setCredentialRef(String credentialRef) {
    this.credentialRef = credentialRef;
  }

  public String getCorpId() {
    return corpId;
  }

  public void setCorpId(String corpId) {
    this.corpId = corpId;
  }
}

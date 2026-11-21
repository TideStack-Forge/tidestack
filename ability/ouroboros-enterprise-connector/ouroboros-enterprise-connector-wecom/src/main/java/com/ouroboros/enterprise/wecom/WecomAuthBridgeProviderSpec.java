package com.ouroboros.enterprise.wecom;

import com.ouroboros.security.externalauth.config.ProviderSpec;

/** Connection-scoped configuration for Enterprise WeChat's provider-specific login bridge. */
public final class WecomAuthBridgeProviderSpec extends ProviderSpec {

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

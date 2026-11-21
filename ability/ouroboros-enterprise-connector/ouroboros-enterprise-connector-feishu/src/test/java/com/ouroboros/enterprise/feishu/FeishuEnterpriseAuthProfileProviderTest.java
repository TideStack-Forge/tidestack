package com.ouroboros.enterprise.feishu;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.enterprise.auth.EnterpriseConnectionAuthProfileRequest;

class FeishuEnterpriseAuthProfileProviderTest {

  private static final String CREDENTIAL_REF = "test:feishu-main";
  @Test
  void derivesFixedOAuthProfileFromPublishedConnectionAndCredentialReference() {
    FeishuEnterpriseAuthProfileProvider provider = new FeishuEnterpriseAuthProfileProvider();
    EnterpriseConnectionAuthProfileRequest request = new EnterpriseConnectionAuthProfileRequest(
        "feishu-main", "feishu", "derived-revision", CREDENTIAL_REF, Map.of());

    var profile = (com.ouroboros.security.externalauth.config.OAuth2ProviderSpec)
        provider.resolve(request).orElseThrow();

    assertThat(provider.provider()).isEqualTo("feishu");
    assertThat(profile.getKey()).isEqualTo("feishu-main");
    assertThat(profile.getType()).isEqualTo("oauth2");
    assertThat(profile.isConnectionScoped()).isTrue();
    assertThat(profile.getConnectionProfileRevision()).isEqualTo("derived-revision");
    assertThat(profile.getAuthorizationUri())
        .isEqualTo("https://accounts.feishu.cn/open-apis/authen/v1/authorize");
    assertThat(profile.getTokenUri()).isEqualTo("https://accounts.feishu.cn/oauth/v3/token");
    assertThat(profile.getUserInfoUri())
        .isEqualTo("https://open.feishu.cn/open-apis/authen/v1/user_info");
    assertThat(profile.getUserInfoDataField()).isEqualTo("data");
    assertThat(profile.getClientId()).isNull();
    assertThat(profile.getClientIdRef()).isEqualTo(CREDENTIAL_REF + "#/appId");
    assertThat(profile.getClientSecret()).isNull();
    assertThat(profile.getClientSecretRef()).isEqualTo(CREDENTIAL_REF + "#/appSecret");
    assertThat(profile.getExternalIdField()).isEqualTo("open_id");
    // 飞书是唯一"登录身份与审批成员标识同源"的渠道，必须显式声明成员标识字段。
    assertThat(profile.getMemberIdField()).isEqualTo("open_id");
    assertThat(profile.getScopes()).isEmpty();
    assertThat(profile.isPkceEnabled()).isTrue();
    assertThat(profile.getPkceMethod()).isEqualTo("S256");
    assertThat(profile.toString()).doesNotContain("client-secret");
  }

  @Test
  void rejectsMissingOrMalformedConnectorCredentialWithoutLeakingIt() {
    FeishuEnterpriseAuthProfileProvider provider = new FeishuEnterpriseAuthProfileProvider();
    assertThat(provider.resolve(new EnterpriseConnectionAuthProfileRequest(
        "feishu-main", "feishu", "derived-revision", null, Map.of())))
        .isEmpty();
    assertThat(provider.resolve(new EnterpriseConnectionAuthProfileRequest(
        "feishu-main", "feishu", "derived-revision", "", Map.of())))
        .isEmpty();
    assertThat(provider.resolve(new EnterpriseConnectionAuthProfileRequest(
        "feishu-main", "feishu", "derived-revision", CREDENTIAL_REF + "#/appId", Map.of())))
        .isEmpty();
    assertThat(provider.resolve(new EnterpriseConnectionAuthProfileRequest(
            "feishu-main", "other", "derived-revision", CREDENTIAL_REF,
            Map.of())))
        .isEmpty();
  }
}

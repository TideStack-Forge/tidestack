package com.ouroboros.enterprise.manage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.ouroboros.enterprise.EnterpriseConnectionProvider;
import com.ouroboros.enterprise.dingtalk.DingtalkConnectionProvider;
import com.ouroboros.enterprise.feishu.FeishuConnectionProvider;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionProviderRegistry;
import com.ouroboros.enterprise.wecom.WecomConnectionProvider;
import com.ouroboros.integration.management.IntegrationManagementPage;
import com.ouroboros.uischema.UISchemaProvider;
import com.ouroboros.util.Json;

class EnterpriseConnectionManagementTest {
  @Test
  void contributesOnlyInstalledProviderPagesWithoutTheWorkspaceHostOrAnApprovalModule() {
    var installed = EnterpriseConnectionProviderRegistry.of(List.of(new WecomConnectionProvider()));
    new ApplicationContextRunner().withUserConfiguration(EnterpriseConnectionManageAutoConfiguration.class)
        .withBean(EnterpriseConnectionProviderRegistry.class, () -> installed).run(context -> {
          assertThat(context).hasNotFailed();
          var pages = context.getBean(EnterpriseConnectionIntegrationContributor.class).pages();
          assertThat(pages).singleElement().satisfies(page -> {
            assertThat(page.section()).isEqualTo(IntegrationManagementPage.Section.CONNECTIONS);
            assertThat(page.title()).isEqualTo("企业微信");
            assertThat(page.schemaPath()).endsWith("/enterprise/wecom");
          });
        });
  }

  @Test
  void newProviderSuppliesItsOwnTitleAndFormFieldsWithoutChangingTheHost() {
    var provider = new EnterpriseConnectionProvider() {
      @Override public String provider() { return "test-provider"; }
      @Override public String displayName() { return "测试企业"; }
      @Override public int maxConnections() { return 1; }
      @Override public Set<String> connectionConfigFields() { return Set.of("enterprise"); }
      @Override public List<Map<String, Object>> credentialFields() {
        return List.of(Map.of("name", "clientSecret", "label", "专用凭据", "required", true));
      }
    };
    var registry = EnterpriseConnectionProviderRegistry.of(List.of(provider));
    assertThat(new EnterpriseConnectionIntegrationContributor(registry).pages()).singleElement()
        .satisfies(page -> assertThat(page.title()).isEqualTo("测试企业"));
    var schema = new EnterpriseConnectionManagementSchemaController(registry).render("test-provider", true, true, true, true);
    var json = Json.toJsonString(schema);
    assertThat(json).contains("connection.enterprise", "credential.clientSecret", "input-password",
        "acknowledgeDisable", "/providers/test-provider/configuration");
    assertThatThrownBy(() -> new EnterpriseConnectionManagementSchemaController(registry).render("absent", true, true, true, true))
        .hasMessageContaining("未安装");
  }

  @Test
  void viewOnlyFormCannotSubmitAndDoesNotExposeSecretInputControls() {
    var registry = EnterpriseConnectionProviderRegistry.of(List.of(new WecomConnectionProvider()));
    var schema = new EnterpriseConnectionManagementSchemaController(registry).render("wecom", false, false, false, false);
    assertThat(schema.get("actions")).isEqualTo(List.of());
    assertThat(Json.toJsonString(schema.get("body"))).doesNotContain("credential.secret", "input-password");
  }

  @Test
  void checkPermissionExposesOnlyDiagnosticsWithoutConfigurationOrSecretControls() {
    var registry = EnterpriseConnectionProviderRegistry.of(List.of(new WecomConnectionProvider()));
    var schema = new EnterpriseConnectionManagementSchemaController(registry).render("wecom", false, false, false, true);
    assertThat(Json.toJsonString(schema.get("actions"))).contains("检查已保存连接", "/verify")
        .doesNotContain("保存企业连接", "调用预算", "凭据维护");
    assertThat(Json.toJsonString(schema.get("body"))).doesNotContain("input-password");
  }

  @Test
  void schemaSpiReferencesTheAuthenticatedAdapterAndRejectsInvalidPaths() {
    var spi = new EnterpriseConnectionUISchemaProvider();
    assertThat(spi.getUISchema(EnterpriseConnectionIntegrationContributor.PATH + "wecom")).isPresent();
    assertThat(spi.getUISchema(EnterpriseConnectionIntegrationContributor.PATH + "wecom/extra")).isEmpty();
    assertThat(spi.getUISchema("/unrelated")).isEmpty();
    assertThat(spi.getUISchema(null)).isEmpty();
    assertThat(java.util.ServiceLoader.load(UISchemaProvider.class).stream()
        .anyMatch(provider -> provider.type() == EnterpriseConnectionUISchemaProvider.class)).isTrue();
  }

  @Test
  void rendersProductionSchemasForEachSelfBuiltProvider() throws Exception {
    var registry = EnterpriseConnectionProviderRegistry.of(List.of(new DingtalkConnectionProvider(),
        new WecomConnectionProvider(), new FeishuConnectionProvider()));
    var renderer = new EnterpriseConnectionManagementSchemaController(registry);
    Map<String, Object> schemas = Map.of("dingtalk", renderer.render("dingtalk", true, true, true, true),
        "wecom", renderer.render("wecom", true, true, true, true),
        "feishu", renderer.render("feishu", true, true, true, true));
    assertThat(Json.toJsonString(schemas)).contains("credential.agentId", "credential.appKey", "credential.appId")
        .doesNotContain("credential.suiteSecret");
    String evidence = System.getProperty("enterprise.schema.evidence");
    if (evidence != null) {
      Files.writeString(Path.of(evidence), Json.toJsonString(schemas));
    }
  }
}

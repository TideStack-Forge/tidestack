package com.ouroboros.workflow.approval.external.runtime.web;

import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.enterprise.EnterpriseConnectionProvider;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionProviderRegistry;
import com.ouroboros.security.annotation.Authority;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapter;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterDescriptor;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterProvider;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.Capability;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.ExternalCallbackCodec;
import com.ouroboros.workflow.approval.external.ExternalCallbackEnvelope;
import com.ouroboros.workflow.approval.external.ExternalCallbackRequest;
import com.ouroboros.workflow.approval.external.ExternalHealth;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinition;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionDiscovery;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionValidator;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionFormReader;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionPage;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionRef;
import com.ouroboros.workflow.approval.external.runtime.ConnectionAwareAdapterResolverTestSupport;
import com.ouroboros.workflow.approval.external.runtime.WorkflowEngineAdapterProviderBeanBridge;
import com.ouroboros.workflow.approval.external.runtime.WorkflowEngineAdapterRegistry;

import io.vavr.control.Try;

/**
 * M13 适配器目录控制器测试：目录列表、定义发现、连接健康检查（404/失败语义）。
 */
class EngineAdapterCatalogControllerTest {

  private WorkflowEngineAdapterRegistry registry;
  private EnterpriseConnectionCatalog connectionConfigProvider;
  private EngineMappingPermissionGuard permissionGuard;
  private EngineAdapterCatalogController controller;

  @BeforeEach
  void setUp() {
    registry = new WorkflowEngineAdapterRegistry(new WorkflowEngineAdapterProviderBeanBridge(List.of()));
    registry.initialize(List.of(new CatalogHealthProvider()));
    connectionConfigProvider = mock(EnterpriseConnectionCatalog.class);
    when(connectionConfigProvider.resolve(any(), any()))
        .thenAnswer(inv -> Optional.of(ConnectionAwareAdapterResolverTestSupport.connection(
            inv.getArgument(1), inv.getArgument(0))));
    permissionGuard = mock(EngineMappingPermissionGuard.class);
    controller = new EngineAdapterCatalogController(
        registry,
        ConnectionAwareAdapterResolverTestSupport.resolver(registry, connectionConfigProvider),
        connectionConfigProvider,
        permissionGuard, EnterpriseConnectionProviderRegistry.of(List.of(new CatalogHealthProvider())));
  }

  @Test
  void listAdaptersReturnsCatalogViewsWithModeFields() {
    var views = controller.listAdapters().getData().getItems();

    assertThat(views).hasSize(1);
    Map<String, Object> view = views.stream().findFirst().orElseThrow();
    assertThat(view.get("adapterId")).isEqualTo("catalog-health");
    assertThat(view.get("provider")).isEqualTo("catalog-health");
    assertThat(view.get("displayName")).isEqualTo("catalog");
    assertThat(view).containsKey("canonicalActions").containsKey("disabledActions")
        .doesNotContainKeys("applicationTypes", "integrationModes",
            "fullProcessActionApplicationTypes", "connectionProfiles");
    assertThat(view.get("disabledActions")).isEqualTo(Set.of());
  }

  @Test
  void connectionEditorSchemaReturnsNativeAmisControlsFromProviderSchema() {
    var schema = controller.connectionEditorSchema("catalog-health").getData();

    assertThat(schema).containsEntry("type", "group");
    assertThat(schema.get("body")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
        .extracting("name")
        .contains("tenantKey");
    assertThat(schema.get("body")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
        .first()
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
        .containsEntry("type", "select")
        .containsKey("options");
  }

  @Test
  void connectionEditorSchemaMapsEveryDeclaredFieldTypeToANativeControl() {
    // amisType 的每个分支都要有行为断言：类型映射错了页面就渲染不出可编辑控件，
    // 而这类错误不会在编译期暴露。
    controller = controllerWithProvider(new SchemaShapeProvider());

    List<Map<String, Object>> body = editorSchemaBody("schema-shape");

    assertThat(body).extracting("name")
        .containsExactlyInAnyOrder("flag", "count", "payload", "items", "plain", "custom");
    assertThat(control(body, "flag")).containsEntry("type", "switch");
    assertThat(control(body, "count")).containsEntry("type", "input-number");
    assertThat(control(body, "payload")).containsEntry("type", "json-editor");
    assertThat(control(body, "items")).containsEntry("type", "combo");
    assertThat(control(body, "plain")).containsEntry("type", "input-text");
    // 显式 editor 优先于 type 映射；label 缺省时回落到字段名。
    assertThat(control(body, "custom")).containsEntry("type", "textarea").containsEntry("label", "custom");
    // 透传字段按声明带入，未声明的键不会凭空出现。
    assertThat(control(body, "custom")).containsEntry("required", true);
  }

  @Test
  void connectionEditorSchemaRejectsProviderSchemaWithoutFieldsArray() {
    controller = controllerWithProvider(new MalformedSchemaProvider(Map.of("provider", "broken")));

    assertThatThrownBy(() -> controller.connectionEditorSchema("broken"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("缺少 fields 数组");
  }

  @Test
  void connectionEditorSchemaRejectsFieldWithoutName() {
    controller = controllerWithProvider(new MalformedSchemaProvider(Map.of(
        "provider", "broken", "fields", List.of(Map.of("type", "string")))));

    assertThatThrownBy(() -> controller.connectionEditorSchema("broken"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("字段缺少 name");
  }

  @Test
  void connectionEditorSchemaRejectsEditorWithoutType() {
    controller = controllerWithProvider(new MalformedSchemaProvider(Map.of(
        "provider", "broken",
        "fields", List.of(Map.of("name", "f", "editor", Map.of("options", List.of()))))));

    assertThatThrownBy(() -> controller.connectionEditorSchema("broken"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("editor 缺少 type");
  }

  @Test
  void connectionEditorSchemaWithoutProviderReturnsEmptyGroup() {
    assertThat(controller.connectionEditorSchema(null).getData())
        .containsEntry("type", "group")
        .extracting("body")
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
        .isEmpty();
  }

  @Test
  void connectionEditorSchemaRejectsUnknownProvider() {
    assertThatThrownBy(() -> controller.connectionEditorSchema("missing"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(EngineIntegrationErrorCodes.ENGINE_ADAPTER_NOT_FOUND);
  }

  @Test
  void everyCatalogEndpointRequiresTheConfigurationAuthority() {
    // 逐个目录端点断言权限：早期版本只反射检查三个方法，恰好漏掉了没有权限标注的
    // listConnections（映射页下拉依赖它），导致"测试通过但端点对外不可用"。
    // 这里改为行为断言——权限守卫拒绝时端点必须抛出，而不是靠读注解。
    org.mockito.Mockito.doThrow(new com.ouroboros.exception.PlatformException("denied"))
        .when(permissionGuard).requirePermission(anyString());

    assertThatThrownBy(() -> controller.listConnections())
        .isInstanceOf(com.ouroboros.exception.PlatformException.class);
    assertThatThrownBy(() -> controller.checkHealth("catalog-health", "conn-1"))
        .isInstanceOf(com.ouroboros.exception.PlatformException.class);

    org.mockito.Mockito.verify(permissionGuard, org.mockito.Mockito.atLeastOnce())
        .requirePermission(anyString());
  }

  @Test
  void discoverDefinitionsReturnsPage() {
    var response = controller.discoverDefinitions("catalog-health", "conn-1", "expense");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().definitions()).hasSize(1);
    assertThat(response.getBody().definitions().get(0).definitionId()).isEqualTo("PROC_1");
  }

  @Test
  void discoverDefinitionsWithoutCatalogPortFails() {
    registry.initialize(List.of(new HealthOnlyProvider("no-catalog")));

    assertThatThrownBy(() -> controller.discoverDefinitions("no-catalog", "conn-1", null))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(EngineIntegrationErrorCodes.EXTERNAL_CAPABILITY_UNSUPPORTED);
  }

  @Test
  void discoverDefinitionsWithUnknownAdapterFails() {
    assertThatThrownBy(() -> controller.discoverDefinitions("no-such", "conn-1", null))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(EngineIntegrationErrorCodes.ENGINE_ADAPTER_NOT_FOUND);
  }

  @Test
  void checkHealthReturnsHealthy() {
    when(connectionConfigProvider.resolve("conn-1", "catalog-health"))
        .thenReturn(Optional.of(new ResolvedEnterpriseConnection("conn-1", Map.of(), null)));

    var response = controller.checkHealth("catalog-health", "conn-1");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().healthy()).isTrue();
  }

  @Test
  void checkHealthWithUnknownAdapterReturnsNotFound() {
    var response = controller.checkHealth("no-such", "conn-1");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void listConnectionsReturnsSafeConnectionSummaries() {
    when(connectionConfigProvider.listSummaries()).thenReturn(List.of(
        new EnterpriseConnectionCatalog.ConnectionSummary("conn-1", "catalog")));

    assertThat(controller.listConnections().getData().getItems())
        .containsExactly(new EnterpriseConnectionCatalog.ConnectionSummary("conn-1", "catalog"));
  }

  @Test
  void checkHealthWithoutConnectionConfigFails() {
    when(connectionConfigProvider.resolve(any(), any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> controller.checkHealth("catalog-health", "conn-1"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(EngineIntegrationErrorCodes.ENGINE_CONNECTION_UNAVAILABLE);
  }

  private EngineAdapterCatalogController controllerWithProvider(
      EnterpriseConnectionProvider provider) {
    WorkflowEngineAdapterRegistry shaped = new WorkflowEngineAdapterRegistry(
        new WorkflowEngineAdapterProviderBeanBridge(List.of()));
    shaped.initialize(List.of((ApprovalWorkflowEngineAdapterProvider) provider));
    return new EngineAdapterCatalogController(
        shaped,
        ConnectionAwareAdapterResolverTestSupport.resolver(shaped, connectionConfigProvider),
        connectionConfigProvider,
        permissionGuard, EnterpriseConnectionProviderRegistry.of(List.of(provider)));
  }

  private List<Map<String, Object>> editorSchemaBody(String provider) {
    Object body = controller.connectionEditorSchema(provider).getData().get("body");
    assertThat(body).isInstanceOf(List.class);
    @SuppressWarnings({"unchecked", "rawtypes"})
    List<Map<String, Object>> typed = (List) body;
    return typed;
  }

  private Map<String, Object> control(List<Map<String, Object>> body, String name) {
    return body.stream()
        .filter(item -> name.equals(item.get("name")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing control: " + name));
  }

  /** 覆盖全部 amisType 分支 + 字符串形态字段 + 显式 editor 的 provider schema。 */
  static final class SchemaShapeProvider implements ApprovalWorkflowEngineAdapterProvider, EnterpriseConnectionProvider {
    @Override
    public String provider() {
      return "schema-shape";
    }

    @Override
    public Map<String, Object> connectionSchema() {
      return Map.of(
          "provider", "schema-shape",
          "fields", List.of(
              Map.of("name", "flag", "type", "boolean"),
              Map.of("name", "count", "type", "integer"),
              Map.of("name", "payload", "type", "object"),
              Map.of("name", "items", "type", "array"),
              Map.of("name", "plain", "type", "string"),
              Map.of("name", "custom", "type", "string", "required", true,
                  "editor", Map.of("type", "textarea", "minRows", 3))));
    }

    @Override
    public ApprovalWorkflowEngineAdapterDescriptor descriptor() {
      return new ApprovalWorkflowEngineAdapterDescriptor("schema-shape", "schema-shape", "1.0", Set.of(), Set.of(), Set.of());
    }

    @Override
    public ApprovalWorkflowEngineAdapter create(ResolvedEnterpriseConnection connection) {
      return new ApprovalWorkflowEngineAdapter() {
        @Override
        public Try<ExternalHealth> health() {
          return Try.success(new ExternalHealth(true, "ok"));
        }
      };
    }
  }

  /** 返回非法 connectionSchema 的 provider，用于异常分支。 */
  static final class MalformedSchemaProvider implements ApprovalWorkflowEngineAdapterProvider, EnterpriseConnectionProvider {
    private final Map<String, Object> schema;

    MalformedSchemaProvider(Map<String, Object> schema) {
      this.schema = schema;
    }

    @Override
    public String provider() {
      return "broken";
    }

    @Override
    public Map<String, Object> connectionSchema() {
      return schema;
    }

    @Override
    public ApprovalWorkflowEngineAdapterDescriptor descriptor() {
      return new ApprovalWorkflowEngineAdapterDescriptor("broken", "broken", "1.0", Set.of(), Set.of(), Set.of());
    }

    @Override
    public ApprovalWorkflowEngineAdapter create(ResolvedEnterpriseConnection connection) {
      return new ApprovalWorkflowEngineAdapter() {
        @Override
        public Try<ExternalHealth> health() {
          return Try.success(new ExternalHealth(true, "ok"));
        }
      };
    }
  }

  /** 实现目录 + 回调端口的测试适配器。 */
  static final class CatalogHealthAdapter
      implements ApprovalWorkflowEngineAdapter, ExternalProcessDefinitionDiscovery,
          ExternalProcessDefinitionValidator, ExternalProcessDefinitionFormReader, ExternalCallbackCodec {
    @Override
    public Try<ExternalHealth> health() {
      return Try.success(new ExternalHealth(true, "ok"));
    }

    @Override
    public Try<ExternalProcessDefinitionPage> discoverDefinitions(
        com.ouroboros.workflow.approval.external.DefinitionDiscoveryRequest request) {
      return Try.success(new ExternalProcessDefinitionPage(List.of(
          new ExternalProcessDefinition("PROC_1", "v1", "示例", "hash-1")), false));
    }

    @Override
    public Try<ExternalProcessDefinition> validateDefinition(ExternalProcessDefinitionRef definition) {
      return Try.success(new ExternalProcessDefinition(definition.definitionId(), definition.definitionVersion(), "示例", null));
    }

    @Override
    public Try<com.ouroboros.workflow.approval.external.ExternalFormSchema> getDefinitionForm(
        ExternalProcessDefinitionRef definition) {
      return Try.success(new com.ouroboros.workflow.approval.external.ExternalFormSchema(List.of(), Map.of()));
    }

    @Override
    public Try<List<ExternalCallbackEnvelope>> verifyAndParseCallback(ExternalCallbackRequest request) {
      return Try.success(List.of());
    }
  }

  static final class CatalogHealthProvider implements ApprovalWorkflowEngineAdapterProvider, EnterpriseConnectionProvider {
    @Override public String provider() { return "catalog-health"; }
    @Override
    public Map<String, Object> connectionSchema() {
      return Map.of(
          "provider", "catalog-health",
          "fields", List.of(Map.of(
              "name", "tenantKey",
              "type", "string",
              "label", "租户标识",
              "required", true,
              "description", "测试租户",
              "editor", Map.of(
                  "type", "select",
                  "options", List.of(Map.of("label", "主租户", "value", "main"))))));
    }

    @Override
    public ApprovalWorkflowEngineAdapterDescriptor descriptor() {
      return new ApprovalWorkflowEngineAdapterDescriptor("catalog-health", "catalog", "1.0", Set.of("approve"), Set.of(Capability.CALLBACK, Capability.EXTERNAL_FINAL_RESULT), Set.of());
    }

    @Override
    public ApprovalWorkflowEngineAdapter create(ResolvedEnterpriseConnection connection) {
      return new CatalogHealthAdapter();
    }
  }

  /** 仅健康检查的测试 provider（无目录端口）。 */
  static final class HealthOnlyProvider implements ApprovalWorkflowEngineAdapterProvider {
    private final String adapterId;

    HealthOnlyProvider(String adapterId) {
      this.adapterId = adapterId;
    }

    @Override
    public ApprovalWorkflowEngineAdapterDescriptor descriptor() {
      return new ApprovalWorkflowEngineAdapterDescriptor(adapterId, "no-catalog", "1.0", Set.of("approve"), Set.of(), Set.of());
    }

    @Override
    public ApprovalWorkflowEngineAdapter create(ResolvedEnterpriseConnection connection) {
      return new ApprovalWorkflowEngineAdapter() {
        @Override
        public Try<ExternalHealth> health() {
          return Try.success(new ExternalHealth(true, "ok"));
        }
      };
    }
  }
}

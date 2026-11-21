package com.ouroboros.notification.enterprise.management;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import com.ouroboros.notification.adapter.*;
import com.ouroboros.notification.employee.NotificationChannelRecipients;
import com.ouroboros.notification.enterprise.*;
import com.ouroboros.notification.template.*;
import com.ouroboros.organizationstructure.*;
import com.ouroboros.security.authorize.*;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.rbac.User;
import com.ouroboros.util.Json;

class EnterpriseNotificationManagementControllerTest {
  private final EmployeeAggregator employees = mock(EmployeeAggregator.class);
  private final EnterpriseNotificationManagementController controller = new EnterpriseNotificationManagementController(employees, List.of());
  private final EnterpriseNotificationRecipients recipients = mock(EnterpriseNotificationRecipients.class);
  private final NotificationAdapterSupplier supplier = mock(NotificationAdapterSupplier.class);
  private final User user = mock(User.class);
  private final DefaultSecurityIdentity identity = new DefaultSecurityIdentity(user);
  private final AuthorizationEvaluator evaluator = mock(AuthorizationEvaluator.class);
  private final Adapter adapter = new Adapter();
  private MockedStatic<AuthorizationEvaluator> authorization;
  private MockedStatic<NotificationAdapterCenter> adapters;
  private MockedStatic<NotificationTemplateCenter> templates;

  @BeforeEach void setup() {
    when(user.getUserIdentity()).thenReturn("admin"); when(user.isEnabled()).thenReturn(true);
    authorization = mockStatic(AuthorizationEvaluator.class); authorization.when(AuthorizationEvaluator::standard).thenReturn(evaluator);
    permit(EnterpriseNotificationManagementController.VIEW); permit(EnterpriseNotificationManagementController.TEST);
    adapters = mockStatic(NotificationAdapterCenter.class); templates = mockStatic(NotificationTemplateCenter.class);
    when(supplier.getName()).thenReturn("installed"); when(supplier.getEmployeeRecipients()).thenReturn(Optional.of(recipients));
    when(supplier.getAdapter()).thenAnswer(call -> adapter);
    adapters.when(() -> NotificationAdapterCenter.getAllSuppliers(List.of())).thenReturn(Map.of("installed", supplier));
    adapters.when(() -> NotificationAdapterCenter.findSupplier("installed", List.of())).thenReturn(Optional.of(supplier));
    var employee = mock(Employee.class); when(employee.getId()).thenReturn("employee-admin");
    when(employees.getByUser("admin")).thenReturn(Optional.of(employee));
    var frozen = Map.<String, Object>of(NotificationChannelRecipients.ROUTING_CONTEXT, Map.of("connectionId", "company"));
    when(recipients.resolve(List.of("employee-admin"), Map.of())).thenReturn(new NotificationChannelRecipients.Resolution(
        frozen, Map.of("employee-admin", List.of("admin-member"))));
  }

  @AfterEach void cleanup() { templates.close(); adapters.close(); authorization.close(); }

  @Test void templateReferencesRequireTheExistingTemplatePermissionAndNeverExposeContents() {
    CurrentSecurityContextHolder.withIdentity(identity, () -> {
      assertThat(Json.toJsonString(controller.read())).contains("installed", "canViewTemplates").doesNotContain("confidential");
      templates.verifyNoInteractions();
      permit("systemSetting.notificationTemplate");
      var template = new DefaultNotificationTemplate(); template.setName("order-notice"); template.setEnabled(true);
      template.setChannel(Map.of("installed", Map.of("schema", Map.of("contentTemplate", "confidential"))));
      templates.when(NotificationTemplateCenter::getAllTemplate).thenReturn(Map.of("order-notice", template));
      assertThat(Json.toJsonString(controller.read())).contains("order-notice", "installed").doesNotContain("confidential", "contentTemplate");
      return null;
    });
  }

  @Test void fixedTestMessageUsesOnlyTheCurrentAdministratorsAssociatedMemberAndFrozenRoute() {
    CurrentSecurityContextHolder.withIdentity(identity, () -> {
      assertThat(controller.test("installed").getData().outcome()).isEqualTo(NotificationChannelResult.Outcome.ACCEPTED);
      assertThat(adapter.addresses).containsExactly("admin-member");
      assertThat(adapter.route).containsEntry("connectionId", "company");
      assertThat(adapter.schema.getTitleTemplate()).isEqualTo("企业通知接入测试");
      assertThat(adapter.schema.getContentTemplate()).contains("不包含业务数据");
      verify(recipients).verify(eq("employee-admin"), eq(List.of("admin-member")), anyMap());
      return null;
    });
  }

  @Test void missingTestPermissionDoesNotResolveAddressesOrSend() {
    reset(evaluator); permit(EnterpriseNotificationManagementController.VIEW);
    CurrentSecurityContextHolder.withIdentity(identity, () -> {
      assertThatThrownBy(() -> controller.test("installed")).hasMessageContaining("无权");
      verifyNoInteractions(employees, recipients); assertThat(adapter.addresses).isNull();
      return null;
    });
  }

  @Test void disabledAccountsMissingEmployeesOrRemovedRelationshipsBlockBeforeSending() {
    CurrentSecurityContextHolder.withIdentity(identity, () -> {
      when(user.isEnabled()).thenReturn(false);
      assertThatThrownBy(() -> controller.test("installed")).hasMessageContaining("停用");
      when(user.isEnabled()).thenReturn(true); when(employees.getByUser("admin")).thenReturn(Optional.empty());
      assertThatThrownBy(() -> controller.test("installed")).hasMessageContaining("未关联员工");
      assertThat(adapter.addresses).isNull(); return null;
    });
  }

  @Test void unknownOutcomesAreReturnedForExplicitInspectionWithoutAutomaticReplay() {
    adapter.outcome = NotificationChannelResult.Outcome.UNKNOWN;
    CurrentSecurityContextHolder.withIdentity(identity, () -> {
      assertThat(controller.test("installed").getData().outcome()).isEqualTo(NotificationChannelResult.Outcome.UNKNOWN);
      assertThat(adapter.calls).isEqualTo(1); return null;
    });
  }

  private void permit(String permission) {
    when(evaluator.evaluate(identity, AuthorizationEvaluator.PLATFORM_API, permission))
        .thenReturn(new AuthorizationDecision.Permit(AccessControlParams.empty()));
  }

  private static class Adapter implements NotificationAdapter<EnterpriseNotificationChannelSchema, EnterpriseNotificationChannelOptions> {
    List<String> addresses;
    Map<String, Object> route;
    EnterpriseNotificationChannelSchema schema;
    int calls;
    NotificationChannelResult.Outcome outcome = NotificationChannelResult.Outcome.ACCEPTED;
    @Override public void send(java.util.Collection<String> addresses, EnterpriseNotificationChannelSchema schema,
        Map<String, Object> payload, EnterpriseNotificationChannelOptions options) { throw new AssertionError("test requires a receipt"); }
    @Override public Class<EnterpriseNotificationChannelSchema> getChannelSchemaClass() { return EnterpriseNotificationChannelSchema.class; }
    @Override public Class<EnterpriseNotificationChannelOptions> getChannelOptionsClass() { return EnterpriseNotificationChannelOptions.class; }
    @Override public Optional<NotificationChannelResult> sendWithResult(String key, java.util.Collection<String> addresses,
        EnterpriseNotificationChannelSchema schema, Map<String, Object> payload, EnterpriseNotificationChannelOptions options, Map<String, Object> context) {
      this.addresses = List.copyOf(addresses); this.route = context; this.schema = schema; calls++;
      return Optional.of(new NotificationChannelResult(outcome, "test-receipt"));
    }
  }
}

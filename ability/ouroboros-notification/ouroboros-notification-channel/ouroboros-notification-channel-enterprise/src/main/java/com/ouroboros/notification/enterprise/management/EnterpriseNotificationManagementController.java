package com.ouroboros.notification.enterprise.management;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import com.ouroboros.enterprise.runtime.management.EnterpriseConnectionAccess;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.notification.adapter.*;
import com.ouroboros.notification.enterprise.EnterpriseNotificationRecipients;
import com.ouroboros.notification.template.NotificationTemplateCenter;
import com.ouroboros.organizationstructure.EmployeeAggregator;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.util.Json;
import com.ouroboros.web.WebApiResult;

/** Connects installed channels to their existing connection and template owners. No template writes. */
@RestController
@RequestMapping("/api/sys/enterprise-notifications")
public final class EnterpriseNotificationManagementController {
  public static final String VIEW = "systemSetting.integrationManagement.connections.notification.view";
  public static final String TEST = "systemSetting.integrationManagement.connections.notification.test";
  private final EmployeeAggregator employees;
  private final List<NotificationAdapterSupplier> managedSuppliers;

  public EnterpriseNotificationManagementController(EmployeeAggregator employees, List<NotificationAdapterSupplier> managedSuppliers) {
    this.employees = employees; this.managedSuppliers = List.copyOf(managedSuppliers);
  }

  @ModelAttribute public void preventCaching(jakarta.servlet.http.HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
  }

  @GetMapping public WebApiResult<?> read() {
    EnterpriseConnectionAccess.require(VIEW);
    boolean templates = EnterpriseConnectionAccess.permits("systemSetting.notificationTemplate");
    var channels = NotificationAdapterCenter.getAllSuppliers(managedSuppliers).values().stream()
        .filter(supplier -> supplier.getEmployeeRecipients().orElse(null) instanceof EnterpriseNotificationRecipients)
        .map(supplier -> Map.of("channel", supplier.getName(), "title", supplier.getManagement()
            .map(NotificationChannelManagement::title).orElse(supplier.getName()))).toList();
    var references = templates ? NotificationTemplateCenter.getAllTemplate().values().stream()
        .flatMap(template -> channels.stream().filter(channel -> template.getChannel() != null
            && template.getChannel().containsKey(channel.get("channel"))).map(channel -> Map.of(
                "channel", channel.get("channel"), "name", template.getName(), "enabled", template.isEnabled()))).toList() : List.of();
    return WebApiResult.ok(Map.of("channels", channels, "templates", references,
        "canViewTemplates", templates, "canTest", EnterpriseConnectionAccess.permits(TEST)));
  }

  @PostMapping("/{channel}/test") public WebApiResult<NotificationChannelResult> test(@PathVariable String channel) {
    EnterpriseConnectionAccess.require(VIEW); EnterpriseConnectionAccess.require(TEST);
    var identity = CurrentSecurityContextHolder.getRequiredIdentity();
    if (!identity.getUserDetail().isEnabled()) { throw new ValidationException("当前账号已停用"); }
    var employee = employees.getByUser(identity.getUserIdentity()).orElseThrow(() -> new ValidationException("当前账号未关联员工，无法发送测试"));
    var supplier = NotificationAdapterCenter.findSupplier(channel, managedSuppliers).orElseThrow(() -> new ValidationException("通知渠道未安装"));
    if (!(supplier.getEmployeeRecipients().orElse(null) instanceof EnterpriseNotificationRecipients recipients)) {
      throw new ValidationException("此渠道未接入企业连接");
    }
    var resolution = recipients.resolve(List.of(employee.getId()), Map.of());
    var addresses = resolution.addresses().get(employee.getId());
    recipients.verify(employee.getId(), addresses, resolution.channelOptions());
    NotificationAdapter<NotificationChannelSchema, NotificationChannelOptions> adapter = supplier.getAdapter();
    if (adapter == null) { throw new ValidationException("通知渠道未就绪"); }
    var schema = Json.toBean(adapter.getChannelSchemaClass(), Json.toJsonString(Map.of(
        "titleTemplate", "企业通知接入测试", "contentTemplate", "这是一条由当前管理员主动发送的接入测试消息，不包含业务数据。")));
    var options = Json.toBean(adapter.getChannelOptionsClass(), "{}");
    var receipt = adapter.sendWithResult(UUID.randomUUID().toString(), addresses, schema, Map.of(), options,
        EnterpriseNotificationRecipients.context(resolution.channelOptions()));
    return WebApiResult.ok(receipt.orElseThrow(() -> new ValidationException("渠道未返回投递结果，请核对后再操作")));
  }
}

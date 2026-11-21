package com.ouroboros.enterprise.manage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.enterprise.runtime.EnterpriseConnectionProviderRegistry;
import com.ouroboros.enterprise.runtime.management.EnterpriseConnectionAccess;
import com.ouroboros.enterprise.runtime.web.EnterpriseConnectionEditor;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.web.WebApiResult;

/** Presentation adapter consumes the installed provider's contract and original protected command. */
@RestController
@RequestMapping("/api/sys/enterprise-connections/providers")
public final class EnterpriseConnectionManagementSchemaController {
  private final EnterpriseConnectionProviderRegistry providers;

  public EnterpriseConnectionManagementSchemaController(EnterpriseConnectionProviderRegistry providers) {
    this.providers = providers;
  }

  @GetMapping("/{provider}/management-schema")
  public WebApiResult<Map<String, Object>> schema(@PathVariable String provider, HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
    EnterpriseConnectionAccess.require(EnterpriseConnectionAccess.VIEW);
    return WebApiResult.ok(render(provider, EnterpriseConnectionAccess.permits(EnterpriseConnectionAccess.CONFIGURE),
        EnterpriseConnectionAccess.permits(EnterpriseConnectionAccess.ROTATE),
        EnterpriseConnectionAccess.permits(EnterpriseConnectionAccess.DISABLE),
        EnterpriseConnectionAccess.permits(EnterpriseConnectionAccess.VERIFY)));
  }

  Map<String, Object> render(String provider, boolean configure, boolean rotate, boolean disable, boolean verify) {
    var descriptor = providers.schemaProviderOf(provider).orElseThrow(() -> new ValidationException("连接类型未安装"));
    if (descriptor.maxConnections() != 1) { throw new ValidationException("此编辑器仅管理单企业连接"); }
    var body = new ArrayList<Map<String, Object>>();
    body.add(Map.of("type", "alert", "level", "info", "body",
        "本期接入企业自建应用，每个渠道在当前部署只连接一个企业。登录、目录、通知和开放 API 共用此连接。"));
    body.add(Map.of("type", "input-text", "name", "connection.connectionId", "label", "连接标识",
        "required", true, "disabledOn", configure ? "${isExisting}" : "true",
        "description", "保存后保持不变，用于登录绑定和其他能力引用。"));
    Object rawFields = new EnterpriseConnectionEditor().render(descriptor.connectionSchema()).get("body");
    if (!(rawFields instanceof List<?> fields)) { throw new ValidationException("连接字段合同无效"); }
    for (Object value : fields) {
      if (!(value instanceof Map<?, ?> field) || !(field.get("name") instanceof String name)) {
        throw new ValidationException("连接字段合同无效");
      }
      Map<String, Object> control = new LinkedHashMap<>();
      field.forEach((key, item) -> { if (key instanceof String text) { control.put(text, item); } });
      control.put("name", "connection." + name);
      control.put("disabledOn", configure ? descriptor.connectionIdentityFields().contains(name) ? "${isExisting}" : "false" : "true");
      body.add(control);
    }
    body.add(Map.of("type", "switch", "name", "connection.enabled", "label", "启用连接",
        "disabledOn", configure && disable ? "false" : configure ? "${initialEnabled}" : disable ? "${!initialEnabled}" : "true"));
    body.add(Map.of("type", "switch", "name", "connection.auth.enabled", "label", "启用企业登录",
        "disabled", !configure, "description", "关闭登录不影响此连接的其他能力；新接入时先保存凭据再按需开启。"));
    body.add(Map.of("type", "tpl", "tpl", "凭据状态：${credentialState.configured ? '已配置' : '未配置'}。密钥保存后不会回显。"));
    body.add(Map.of("type", "input-text", "name", "businessCallbackUrl", "label", "业务回调地址",
        "disabled", true, "clearable", false,
        "description", "在企业平台后台配置此地址。公开平台地址未设置时显示路径，请补全平台后端的对外域名。接收业务事件需要填写对应回调验证材料；此地址与登录回调分开。"));
    if (rotate) {
      body.add(Map.of("type", "switch", "name", "replaceCredential", "label", "填写或替换应用凭据",
          "description", "不勾选则保留原凭据。勾选后填写完整应用凭据；App ID/AgentID 的变更须使用新连接。"));
      for (var field : descriptor.credentialFields()) {
        Map<String, Object> control = new LinkedHashMap<>(field);
        control.put("type", "input-password");
        control.put("name", "credential." + field.get("name"));
        control.put("visibleOn", "${replaceCredential}");
        control.put("required", false);
        control.put("requiredOn", Boolean.TRUE.equals(field.get("required")) ? "${replaceCredential}" : "false");
        control.put("autoComplete", "new-password");
        body.add(control);
      }
      body.add(Map.of("type", "alert", "level", "info", "visibleOn", "${replaceCredential}", "body",
          "回调验证字段仅在接收业务回调时需要。旧第三方套件连接的应用形态保持原样，不能通过此自建应用表单切换。"));
    }
    body.add(Map.of("type", "alert", "level", "warning", "visibleOn", "${initialEnabled && !connection.enabled}",
        "body", "停用后，新登录、通知、同步和 API 调用将被拒绝。请自行处理在途业务，并核对企业平台后台配置。"));
    body.add(Map.of("type", "checkbox", "name", "acknowledgeDisable", "option", "已了解停用影响，由管理员负责后续处理",
        "visibleOn", "${initialEnabled && !connection.enabled}", "requiredOn", "${initialEnabled && !connection.enabled}"));
    var form = new LinkedHashMap<String, Object>();
    form.put("type", "form");
    form.put("title", descriptor.displayName() + "企业连接");
    form.put("initApi", "/api/sys/enterprise-connections/providers/" + provider + "/configuration");
    form.put("api", Map.of("method", "put", "url", "/api/sys/enterprise-connections",
        "requestAdaptor", "const data = {...api.data}; delete data.replaceCredential; delete data.initialEnabled; delete data.isExisting; delete data.credentialState; delete data.businessCallbackUrl; if (!api.data.replaceCredential) delete data.credential; return {...api, data};",
        "data", Map.of("&", "$$")));
    form.put("body", body);
    var actions = new ArrayList<Map<String, Object>>();
    if (configure || rotate || disable) { actions.add(Map.of("type", "submit", "level", "primary", "label", "保存企业连接")); }
    if (configure) { actions.add(budgetSettings(provider)); }
    if (rotate) { actions.add(credentialMaintenance()); }
    if (verify) { actions.add(verifyConnection()); }
    form.put("actions", actions);
    form.put("onEvent", Map.of("submitSucc", Map.of("actions", List.of(Map.of("actionType", "setValue",
        "args", Map.of("value", Map.of("credential", Map.of(), "replaceCredential", false, "acknowledgeDisable", false)))))));
    return form;
  }

  private Map<String, Object> verifyConnection() {
    return Map.of("type", "button", "label", "检查已保存连接", "actionType", "dialog",
        "disabledOn", "${!isExisting || !initialEnabled}", "dialog", Map.of(
            "title", "检查已保存连接", "body", List.of(
                Map.of("type", "alert", "level", "info", "body",
                    "只检查已保存且已启用连接的应用凭据，不提交当前草稿，也不发送业务消息。目录、通知和登录权限需分别验证。"),
                Map.of("type", "service", "api", Map.of("method", "post",
                    "url", "/api/sys/enterprise-connections/${connection.connectionId}/verify",
                    "requestAdaptor", "return {...api, data: {}};"),
                    "body", List.of(Map.of("type", "tpl", "tpl", "${message}"),
                        Map.of("type", "static", "name", "checkedAt", "label", "检查时间"))))));
  }

  private Map<String, Object> budgetSettings(String provider) {
    return Map.of("type", "button", "label", "调用预算", "actionType", "dialog", "dialog", Map.of(
        "title", "渠道共享调用预算", "body", Map.of("type", "form",
            "initApi", "/api/sys/enterprise-connections/providers/" + provider + "/budget",
            "api", Map.of("method", "put", "url", "/api/sys/enterprise-connections/providers/" + provider + "/budget",
                "data", Map.of("requestsPerSecond", "${requestsPerSecond}", "expectedRevision", "${expectedRevision}")),
            "body", List.of(Map.of("type", "alert", "level", "info", "body",
                "请根据企业后台额度设置保守的部署总速率。登录、目录、通知、审批及 RPC 共享此预算；各节点按部署节点数分配。未设置时拒绝开放 API 请求。"),
                Map.of("type", "input-number", "name", "requestsPerSecond", "label", "每秒请求总数",
                    "required", true, "min", 1, "max", 1000000, "precision", 0)))));
  }

  private Map<String, Object> credentialMaintenance() {
    var clear = Map.of("type", "button", "label", "清除", "actionType", "ajax", "level", "danger",
        "confirmText", "确认清除此凭据？服务端会重新核对所有连接引用；仍被引用的凭据不能清除。",
        "api", Map.of("method", "post", "url", "/api/sys/enterprise-connections/credentials/${connectionId}/clear",
            "data", Map.of("expectedRevisions", "${expectedRevisions}")), "reload", "enterprise-credential-maintenance");
    return Map.of("type", "button", "label", "凭据维护", "actionType", "dialog", "dialog", Map.of(
        "title", "孤立凭据清理", "body", Map.of("type", "service", "name", "enterprise-credential-maintenance",
            "api", "/api/sys/enterprise-connections", "body", List.of(
                Map.of("type", "alert", "level", "info", "body", "仅展示不再对应连接的凭据候选。停用连接仍保留引用；已移除插件的凭据也受保护。"),
                Map.of("type", "crud", "source", "${orphanCredentials}", "columns", List.of(
                    Map.of("name", "connectionId", "label", "原连接标识"), Map.of("name", "provider", "label", "渠道"),
                    Map.of("name", "credentialVersion", "label", "凭据版本"),
                    Map.of("type", "operation", "label", "操作", "buttons", List.of(clear))))))));
  }
}

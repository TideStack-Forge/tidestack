package com.ouroboros.notification.behavior;

import java.util.Arrays;
import java.util.List;

import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorContribution;
import com.ouroboros.businessmodel.behavior.EventSubscriptionDefinition;
import com.ouroboros.businessmodel.behavior.BehaviorDefinitionSource;
import com.ouroboros.businessmodel.behavior.BehaviorMemberSource;
import com.ouroboros.businessmodel.event.BusinessWorkflowNotificationSource;
import com.ouroboros.businessmodel.meta.BusinessModelMeta;

/** Four conditional built-in rules reference the original workflow template properties; no source metadata is rewritten. */
public final class WorkflowNotificationSubscriptions {
  public List<BusinessModelBehaviorContribution> contribute(BusinessModelMeta model) {
    String flowKey = model.getExtraPropDeep(String.class, "workflow", "bindings", "0", "processId").orElse(null);
    if (flowKey == null || flowKey.isBlank()) { return List.of(); }
    var subscriptions = Arrays.stream(BusinessWorkflowNotificationSource.Rule.values()).map(rule -> {
      var subscription = new EventSubscriptionDefinition(); subscription.setName(rule.subscriptionName());
      subscription.getSourceEvent().setModelFullName(model.getFullName()); subscription.getSourceEvent().setEventName(rule.eventName());
      subscription.getTarget().setKind("listener"); subscription.getTarget().setListenerName(BusinessWorkflowNotificationSource.LISTENER_NAME);
      subscription.getTarget().setConfig(rule.listenerConfig()); subscription.setExecutionMode(com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_INDEPENDENT);
      return subscription;
    }).toList();
    var sources = Arrays.stream(BusinessWorkflowNotificationSource.Rule.values()).map(rule ->
        new BehaviorMemberSource("subscription", rule.subscriptionName(), "configuration", "generated",
            new BehaviorDefinitionSource("approval-workflow-definition:" + flowKey, "notification." + rule.templateProperty(), "2.0"),
            "条件生成通知规则：沿源事实捕获原模板、节点通知开关及收件规则，交付前重验当前接收资格；规则存在不代表本次一定发送，未匹配或已过期按原规则跳过")).toList();
    return List.of(new BusinessModelBehaviorContribution(model.getFullName(), "workflow:" + flowKey + ".notification",
        List.of(), List.of(), subscriptions, "2.0", "generated", sources));
  }
}

package com.ouroboros.businessmodel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ouroboros.businessmodel.behavior.ActionDefinition;
import com.ouroboros.businessmodel.behavior.EventDefinition;
import com.ouroboros.businessmodel.behavior.EventSubscriptionDefinition;
import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.util.Json;

final class BusinessModelRevision {
  private BusinessModelRevision() {
  }

  static String compute(BusinessModelMeta modelMeta,
                        List<ActionDefinition> actions,
                        List<EventDefinition> events,
                        List<EventSubscriptionDefinition> subscriptions,
                        Map<String, com.ouroboros.businessmodel.action.BusinessActionMutationEventPlan> mutationPlans,
                        Map<String, com.ouroboros.businessmodel.behavior.BehaviorDefinitionSource> actionSources,
                        Map<String, com.ouroboros.businessmodel.behavior.BehaviorDefinitionSource> subscriptionSources, Map<String, String> executionAuthorities,
                        List<com.ouroboros.businessmodel.behavior.BehaviorMemberSource> memberSources) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("model", modelMeta);
    snapshot.put("actions", actions);
    snapshot.put("events", events);
    snapshot.put("eventSubscriptions", subscriptions);
    snapshot.put("mutationEventPlans", mutationPlans);
    snapshot.put("actionSources", actionSources);
    snapshot.put("subscriptionSources", subscriptionSources);
    snapshot.put("executionAuthorities", executionAuthorities);
    snapshot.put("memberSources", memberSources);
    String serialized = Json.tryToJsonString(snapshot)
        .getOrElseThrow(error -> new IllegalStateException("业务模型发布快照无法计算 revision", error));
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(serialized.getBytes(StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("当前 Java 运行时不支持 SHA-256", error);
    }
  }
}

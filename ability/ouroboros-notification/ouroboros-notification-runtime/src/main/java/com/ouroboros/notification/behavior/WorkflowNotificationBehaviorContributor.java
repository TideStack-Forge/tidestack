package com.ouroboros.notification.behavior;

import java.util.List;

import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorContribution;
import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorContributor;
import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.common.app.util.SpringContextUtil;

/** ServiceLoader bridge to the optional integration bean, using the platform's existing context owner. */
public final class WorkflowNotificationBehaviorContributor implements BusinessModelBehaviorContributor {
  @Override
  public List<BusinessModelBehaviorContribution> getContributions() { return List.of(); }

  @Override
  public List<BusinessModelBehaviorContribution> getContributions(BusinessModelMeta modelMeta) {
    var context = SpringContextUtil.getApplicationContext();
    if (context == null) { return List.of(); }
    var subscriptions = context.getBeanProvider(WorkflowNotificationSubscriptions.class).getIfAvailable();
    return subscriptions == null ? List.of() : subscriptions.contribute(modelMeta);
  }
}

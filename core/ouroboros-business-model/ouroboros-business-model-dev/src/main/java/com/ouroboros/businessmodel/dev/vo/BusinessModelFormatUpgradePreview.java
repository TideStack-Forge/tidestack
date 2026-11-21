package com.ouroboros.businessmodel.dev.vo;

import java.util.List;

import com.ouroboros.businessmodel.meta.BusinessModelMeta;

/** Stateless upgrade preview: application must recompute and match the complete plan. */
public record BusinessModelFormatUpgradePreview(String requestId, String planFingerprint,
    BusinessModelMeta upgradedModel, List<BusinessModelResourceChange> resources,
    BusinessModelBehaviorPreview before, BusinessModelBehaviorPreview after) {
  public BusinessModelFormatUpgradePreview { resources = List.copyOf(resources); }
}

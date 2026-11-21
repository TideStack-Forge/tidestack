package com.ouroboros.businessmodel.dev.vo;

import java.util.List;

import com.ouroboros.businessmodel.meta.BusinessModelMeta;

public record BusinessModelScriptActionPreview(String requestId, String planFingerprint,
    BusinessModelMeta businessModel, String actionName, String flowName, List<BusinessModelResourceChange> resources,
    BusinessModelBehaviorPreview validation) {
  public BusinessModelScriptActionPreview { resources = List.copyOf(resources); }
}

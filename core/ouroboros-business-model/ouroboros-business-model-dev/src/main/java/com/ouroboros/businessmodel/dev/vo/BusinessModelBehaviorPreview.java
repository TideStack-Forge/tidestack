package com.ouroboros.businessmodel.dev.vo;

import com.ouroboros.businessmodel.behavior.BusinessBehaviorCatalog;

/** Draft identity is echoed so consumers can discard responses superseded by a later edit. */
public record BusinessModelBehaviorPreview(String view, String requestId, String draftFingerprint,
    String codeContributionCoverage, BusinessBehaviorCatalog catalog, BusinessModelValidationSnapshot validationSnapshot) { }

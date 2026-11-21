package com.ouroboros.statistics.reporting.dev.service;

import com.ouroboros.statistics.reporting.dev.model.governance.AssetReferenceGraph;

public interface AssetReferenceGraphService {
  AssetReferenceGraph resolve(String appName);
}

package com.ouroboros.statistics.reporting.dev.service;

import java.util.List;

import com.ouroboros.statistics.reporting.dev.model.editor.DatasetReferenceSummary;

public interface DatasetReferenceResolver {
  List<DatasetReferenceSummary> resolve(String datasetName, boolean enabledOnly);
}

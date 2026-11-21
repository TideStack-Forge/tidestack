package com.ouroboros.statistics.reporting.dev.service;

import java.util.Optional;

import com.ouroboros.statistics.reporting.dev.model.editor.DatasetEditorShell;

public interface DatasetEditorService {
  Optional<DatasetEditorShell> loadShell(String appName, String datasetName);
}

package com.ouroboros.statistics.reporting.dev.controller;

import java.util.Objects;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.statistics.reporting.dev.model.editor.DatasetEditorShell;
import com.ouroboros.statistics.reporting.dev.service.DatasetEditorService;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/dev/{appName}/dataset")
public class DatasetEditorController {
  private final DatasetEditorService datasetEditorService;

  public DatasetEditorController(DatasetEditorService datasetEditorService) {
    this.datasetEditorService = Objects.requireNonNull(datasetEditorService, "datasetEditorService is required");
  }

  @GetMapping("/{datasetName:.+}/editor")
  public WebApiResult<DatasetEditorShell> shell(@PathVariable String appName,
                                                @PathVariable String datasetName) {
    return WebApiResult.of(datasetEditorService.loadShell(appName, datasetName));
  }
}

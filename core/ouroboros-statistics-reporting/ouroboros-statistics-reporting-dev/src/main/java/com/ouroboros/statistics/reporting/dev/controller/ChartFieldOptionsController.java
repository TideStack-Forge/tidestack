package com.ouroboros.statistics.reporting.dev.controller;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.statistics.reporting.definition.schema.DatasetFieldDefinition;
import com.ouroboros.statistics.reporting.dev.model.LowcodeDatasetDefinition;
import com.ouroboros.statistics.reporting.dev.service.impl.DatasetDevService;
import com.ouroboros.statistics.reporting.validation.ChartParameterRules;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.SelectOption;

@RestController
@RequestMapping("/api/dev/{appName}/chart/field-options")
public class ChartFieldOptionsController {
  private final DatasetDevService datasetDevService;

  public ChartFieldOptionsController(DatasetDevService datasetDevService) {
    this.datasetDevService = Objects.requireNonNull(datasetDevService, "datasetDevService is required");
  }

  @GetMapping("/{datasetName:.+}")
  public ItemsWebApiResult<SelectOption> options(@PathVariable("appName") String appName,
                                                 @PathVariable("datasetName") String datasetName,
                                                 @RequestParam("chartType") String chartType,
                                                 @RequestParam("param") String parameterName) {
    Optional<LowcodeDatasetDefinition> dataset = datasetDevService.getOne(datasetName);
    if (!dataset.isPresent()) {
      throw new PlatformException("dataset definition not found: " + datasetName);
    }
    if (!ChartParameterRules.supportsChartType(chartType)) {
      throw new PlatformException("unsupported chartType: " + chartType);
    }
    if (!ChartParameterRules.allowedParametersFor(chartType).contains(parameterName)
        && !("combo".equals(chartType) && "series.field".equals(parameterName))) {
      throw new PlatformException("parameter is not supported by chartType: " + parameterName);
    }
    List<SelectOption> options = filterFields(dataset.get(), chartType, parameterName).stream()
        .map(this::toOption)
        .collect(Collectors.toList());
    return ItemsWebApiResult.ok(options);
  }

  private List<DatasetFieldDefinition> filterFields(LowcodeDatasetDefinition dataset,
                                                    String chartType,
                                                    String parameterName) {
    if (dataset == null || dataset.getSchema() == null || dataset.getSchema().getFields() == null) {
      return new ArrayList<DatasetFieldDefinition>();
    }

    List<DatasetFieldDefinition> result = new ArrayList<DatasetFieldDefinition>();

    for (DatasetFieldDefinition field : dataset.getSchema().getFields()) {
      if (field == null || isBlank(field.getName())) {
        continue;
      }
      if (ChartParameterRules.accepts(chartType, parameterName, field)) {
        result.add(field);
      }
    }
    return result;
  }

  private SelectOption toOption(DatasetFieldDefinition field) {
    String label = isBlank(field.getTitle()) ? field.getName() : field.getTitle() + " (" + field.getName() + ")";
    SelectOption option = new SelectOption(field.getName(), label);
    option.addExtraProp("semanticRole", field.getSemanticRole());
    option.addExtraProp("dataType", field.getDataType());
    return option;
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

}

package com.ouroboros.businessmodel.validation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.validation.exception.BusinessDefineFeatureError;

public class FeatureValidator implements ModelDetailValidator<DefaultBusinessModel> {
  private static final List<List<String>> REQUIRED_WORKFLOW_PATHS = Arrays.asList(
      Arrays.asList("workflow", "bindings", "0", "key"),
      Arrays.asList("workflow", "bindings", "0", "processId"),
      Arrays.asList("workflow", "bindings", "0", "statusField"),
      Arrays.asList("workflow", "bindings", "0", "statusMap", "draft", "code"),
      Arrays.asList("workflow", "bindings", "0", "statusMap", "draft", "label"),
      Arrays.asList("workflow", "bindings", "0", "statusMap", "submitted", "code"),
      Arrays.asList("workflow", "bindings", "0", "statusMap", "submitted", "label"),
      Arrays.asList("workflow", "bindings", "0", "statusMap", "pending", "code"),
      Arrays.asList("workflow", "bindings", "0", "statusMap", "pending", "label"),
      Arrays.asList("workflow", "bindings", "0", "statusMap", "processing", "code"),
      Arrays.asList("workflow", "bindings", "0", "statusMap", "processing", "label"),
      Arrays.asList("workflow", "bindings", "0", "statusMap", "suspended", "code"),
      Arrays.asList("workflow", "bindings", "0", "statusMap", "suspended", "label"),
      Arrays.asList("workflow", "bindings", "0", "statusMap", "withdrawn", "code"),
      Arrays.asList("workflow", "bindings", "0", "statusMap", "withdrawn", "label"),
      Arrays.asList("workflow", "bindings", "0", "statusMap", "approved", "code"),
      Arrays.asList("workflow", "bindings", "0", "statusMap", "approved", "label"),
      Arrays.asList("workflow", "bindings", "0", "statusMap", "veto", "code"),
      Arrays.asList("workflow", "bindings", "0", "statusMap", "veto", "label"),
      Arrays.asList("workflow", "bindings", "0", "statusMap", "canceled", "code"),
      Arrays.asList("workflow", "bindings", "0", "statusMap", "canceled", "label"),
      Arrays.asList("workflow", "bindings", "0", "statusMap", "terminated", "code"),
      Arrays.asList("workflow", "bindings", "0", "statusMap", "terminated", "label"),
      Arrays.asList("workflow", "bindings", "0", "trigger")
  );

  @Override
  public void doValidate(ValidationContext<DefaultBusinessModel> context) {
    DefaultBusinessModel model = context.getModel();
    if (!model.getExtraPropDeep(Boolean.class, "workflow", "isEnabled").orElse(false)) {
      return;
    }

    List<String> missingPaths = new ArrayList<>();
    List<String> invalidValues = new ArrayList<>();
    var bindingCount = model.getExtraPropDeep(List.class, "workflow", "bindings")
        .map(List::size)
        .orElse(0);
    if (bindingCount != 1) {
      missingPaths.add("workflow.bindings");
    }
    for (List<String> path : REQUIRED_WORKFLOW_PATHS) {
      String value = model.getExtraPropDeep(String.class, path).orElse("");
      if (StringUtils.isBlank(value)) {
        missingPaths.add(formatPath(path));
      }
    }
    if (!"default".equals(model.getExtraPropDeep(String.class, "workflow", "bindings", "0", "key").orElse(null))) {
      invalidValues.add("workflow.bindings[0].key");
    }
    if (!"manual".equals(model.getExtraPropDeep(String.class, "workflow", "bindings", "0", "trigger").orElse(null))) {
      invalidValues.add("workflow.bindings[0].trigger");
    }

    if (!missingPaths.isEmpty() || !invalidValues.isEmpty()) {
      List<String> errors = new ArrayList<>();
      if (!missingPaths.isEmpty()) {
        errors.add("缺少必要配置 " + String.join(", ", missingPaths));
      }
      if (!invalidValues.isEmpty()) {
        errors.add("固定配置错误 " + String.join(", ", invalidValues));
      }
      context.addError(new BusinessDefineFeatureError(
          "功能配置错误：工作流" + String.join("；", errors)
      ));
    }
  }

  private String formatPath(List<String> path) {
    var formattedPath = new ArrayList<String>();
    for (int index = 0; index < path.size(); index++) {
      String segment = path.get(index);
      if (StringUtils.isNumeric(segment) && !formattedPath.isEmpty()) {
        int previousIndex = formattedPath.size() - 1;
        formattedPath.set(previousIndex, formattedPath.get(previousIndex) + "[" + segment + "]");
      } else {
        formattedPath.add(segment);
      }
    }
    return String.join(".", formattedPath);
  }
}

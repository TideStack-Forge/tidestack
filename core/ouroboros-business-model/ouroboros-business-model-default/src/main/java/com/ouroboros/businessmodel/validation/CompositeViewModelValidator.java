package com.ouroboros.businessmodel.validation;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ouroboros.businessmodel.CompositeViewBusinessModel;
import com.ouroboros.businessmodel.meta.CompositeViewBusinessModelMeta;
import com.ouroboros.businessmodel.validation.exception.BusinessDefineFeatureError;

public class CompositeViewModelValidator implements ModelValidator<CompositeViewBusinessModel> {
  private static final Set<String> ALLOWED_FORMATS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
      "text", "number", "integer", "decimal", "percent", "currency", "date", "datetime", "boolean", "enum"
  )));
  private static final Set<String> ALLOWED_WIDGETS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
      "input", "number", "date", "datetime", "select", "multiSelect", "checkbox", "switch"
  )));
  private static final Set<String> ALLOWED_SCOPES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList("row", "batch")));
  private static final Set<String> ALLOWED_DIRECTIONS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList("asc", "desc")));
  private static final Set<String> ALLOWED_FILTER_OPS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
      "eq", "neq", "gt", "gte", "lt", "lte", "in", "notIn", "like",
      "isNull", "isNotNull"
  )));

  @Override
  public ValidationResult validate(CompositeViewBusinessModel model) {
    ValidationContext<CompositeViewBusinessModel> context = new ValidationContext<CompositeViewBusinessModel>(model);
    validateSource(context, model);
    validateColumns(context, model.getColumns());
    validateFilterConfig(context, model.getFilterConfig());
    validateActionButtons(context, model.getActionButtons());
    validateLayoutConfig(context, model.getLayoutConfig());
    return new ValidationResult(context.getErrors());
  }

  private void validateSource(ValidationContext<CompositeViewBusinessModel> context,
                              CompositeViewBusinessModel model) {
    boolean hasDatasetName = !isBlank(model.getDatasetName());
    boolean hasEmbeddedQuery = model.getEmbeddedQuery() != null;
    if (!hasDatasetName && !hasEmbeddedQuery) {
      context.addError(new BusinessDefineFeatureError("composite-view.source-missing"));
    }
    if (hasDatasetName && hasEmbeddedQuery) {
      context.addError(new BusinessDefineFeatureError("composite-view.source-conflict"));
    }
  }

  private void validateColumns(ValidationContext<CompositeViewBusinessModel> context,
                               List<CompositeViewBusinessModelMeta.ColumnDef> columns) {
    if (columns == null || columns.isEmpty()) {
      context.addError(new BusinessDefineFeatureError("composite-view.columns-required"));
      return;
    }
    Set<String> fields = new HashSet<String>();
    for (int i = 0; i < columns.size(); i++) {
      CompositeViewBusinessModelMeta.ColumnDef column = columns.get(i);
      if (column == null) {
        context.addError(new BusinessDefineFeatureError("composite-view.columns[" + i + "]-required"));
        continue;
      }
      if (isBlank(column.getField())) {
        context.addError(new BusinessDefineFeatureError("dataset.field-not-allowed: columns[" + i + "].field"));
      } else if (!fields.add(column.getField())) {
        context.addError(new BusinessDefineFeatureError("dataset.field-not-allowed: duplicate column " + column.getField()));
      }
      if (isBlank(column.getTitle())) {
        context.addError(new BusinessDefineFeatureError("composite-view.columns[" + i + "].title-required"));
      }
      if (!isBlank(column.getFormat()) && !ALLOWED_FORMATS.contains(column.getFormat())) {
        context.addError(new BusinessDefineFeatureError("composite-view.columns[" + i + "].format-invalid"));
      }
    }
  }

  private void validateFilterConfig(ValidationContext<CompositeViewBusinessModel> context,
                                    CompositeViewBusinessModelMeta.FilterConfig filterConfig) {
    if (filterConfig == null || filterConfig.getItems() == null) {
      return;
    }
    for (int i = 0; i < filterConfig.getItems().size(); i++) {
      CompositeViewBusinessModelMeta.FilterItemDef item = filterConfig.getItems().get(i);
      if (item == null) {
        context.addError(new BusinessDefineFeatureError("composite-view.filterConfig.items[" + i + "]-required"));
        continue;
      }
      if (isBlank(item.getField())) {
        context.addError(new BusinessDefineFeatureError("dataset.field-not-allowed: filterConfig.items[" + i + "].field"));
      }
      if (isBlank(item.getWidget()) || !ALLOWED_WIDGETS.contains(item.getWidget())) {
        context.addError(new BusinessDefineFeatureError("composite-view.filterConfig.items[" + i + "].widget-invalid"));
      }
      if (item.getOps() == null || item.getOps().isEmpty()) {
        context.addError(new BusinessDefineFeatureError("dataset.filter-op-not-allowed: filterConfig.items[" + i + "].ops"));
        continue;
      }
      for (String op : item.getOps()) {
        if (isBlank(op) || !ALLOWED_FILTER_OPS.contains(op)) {
          context.addError(new BusinessDefineFeatureError("dataset.filter-op-not-allowed: " + op));
        }
      }
    }
  }

  private void validateActionButtons(ValidationContext<CompositeViewBusinessModel> context,
                                     List<CompositeViewBusinessModelMeta.ActionButtonDef> actionButtons) {
    if (actionButtons == null) {
      return;
    }
    for (int i = 0; i < actionButtons.size(); i++) {
      CompositeViewBusinessModelMeta.ActionButtonDef button = actionButtons.get(i);
      if (button == null) {
        context.addError(new BusinessDefineFeatureError("composite-view.actionButtons[" + i + "]-required"));
        continue;
      }
      if (isBlank(button.getLabel())) {
        context.addError(new BusinessDefineFeatureError("composite-view.actionButtons[" + i + "].label-required"));
      }
      if (isBlank(button.getScope()) || !ALLOWED_SCOPES.contains(button.getScope())) {
        context.addError(new BusinessDefineFeatureError("composite-view.actionButtons[" + i + "].scope-invalid"));
      }
      if (isBlank(button.getApiRef())) {
        context.addError(new BusinessDefineFeatureError("composite-view.action-api-invalid"));
      }
      if (button.getContextBinding() == null) {
        context.addError(new BusinessDefineFeatureError("composite-view.actionButtons[" + i + "].contextBinding-required"));
      } else {
        validateContextBinding(context, i, button.getContextBinding());
      }
    }
  }

  private void validateContextBinding(ValidationContext<CompositeViewBusinessModel> context,
                                      int index,
                                      Map<String, String> contextBinding) {
    for (Map.Entry<String, String> entry : contextBinding.entrySet()) {
      if (isBlank(entry.getKey()) || isBlank(entry.getValue())) {
        context.addError(new BusinessDefineFeatureError(
            "composite-view.actionButtons[" + index + "].contextBinding-invalid"));
      }
    }
  }

  private void validateLayoutConfig(ValidationContext<CompositeViewBusinessModel> context,
                                    CompositeViewBusinessModelMeta.LayoutConfig layoutConfig) {
    if (layoutConfig == null) {
      context.addError(new BusinessDefineFeatureError("composite-view.layoutConfig-required"));
      return;
    }
    if (layoutConfig.getPageSize() == null || layoutConfig.getPageSize().intValue() <= 0) {
      context.addError(new BusinessDefineFeatureError("dataset.page-invalid: layoutConfig.pageSize"));
    }
    if (layoutConfig.getDefaultSort() == null) {
      return;
    }
    for (int i = 0; i < layoutConfig.getDefaultSort().size(); i++) {
      CompositeViewBusinessModelMeta.SortDef sort = layoutConfig.getDefaultSort().get(i);
      if (sort == null) {
        context.addError(new BusinessDefineFeatureError("composite-view.layoutConfig.defaultSort[" + i + "]-required"));
        continue;
      }
      if (isBlank(sort.getField())) {
        context.addError(new BusinessDefineFeatureError("dataset.field-not-allowed: layoutConfig.defaultSort[" + i + "].field"));
      }
      if (isBlank(sort.getDirection()) || !ALLOWED_DIRECTIONS.contains(sort.getDirection())) {
        context.addError(new BusinessDefineFeatureError("composite-view.layoutConfig.defaultSort[" + i + "].direction-invalid"));
      }
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}

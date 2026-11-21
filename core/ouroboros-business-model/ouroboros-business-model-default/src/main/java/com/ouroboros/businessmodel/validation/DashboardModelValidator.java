package com.ouroboros.businessmodel.validation;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.ouroboros.businessmodel.DashboardBusinessModel;
import com.ouroboros.businessmodel.meta.DashboardBusinessModelMeta;
import com.ouroboros.businessmodel.validation.exception.BusinessDefineFeatureError;

public class DashboardModelValidator implements ModelValidator<DashboardBusinessModel> {
  private static final Set<String> ALLOWED_WIDGET_TYPES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
      "chart"
  )));
  private static final int MIN_WIDGET_COLS = 6;
  private static final int MIN_WIDGET_ROWS = 6;
  private static final int MAX_WIDGET_COLS = 24;
  private static final int MAX_WIDGET_ROWS = 24;
  private static final int MAX_LAYOUT_COLS = 24;
  @Override
  public ValidationResult validate(DashboardBusinessModel model) {
    ValidationContext<DashboardBusinessModel> context = new ValidationContext<DashboardBusinessModel>(model);
    validateLayout(context, model.getLayout());
    validateWidgets(context, model.getWidgets(), layoutCols(model.getLayout()));
    return new ValidationResult(context.getErrors());
  }

  private void validateLayout(ValidationContext<DashboardBusinessModel> context,
                              DashboardBusinessModelMeta.LayoutConfig layout) {
    if (layout == null) {
      context.addError(new BusinessDefineFeatureError("dashboard.layout-required"));
      return;
    }
    if (layout.getCols() == null || layout.getCols().intValue() <= 0) {
      context.addError(new BusinessDefineFeatureError("dashboard.layout.cols-invalid"));
    } else if (layout.getCols().intValue() > MAX_LAYOUT_COLS) {
      context.addError(new BusinessDefineFeatureError("dashboard.layout.cols-out-of-range"));
    }
    if (layout.getRowHeight() == null || layout.getRowHeight().intValue() <= 0) {
      context.addError(new BusinessDefineFeatureError("dashboard.layout.rowHeight-invalid"));
    }
  }

  private void validateWidgets(ValidationContext<DashboardBusinessModel> context,
                               List<DashboardBusinessModelMeta.WidgetDef> widgets,
                               Integer layoutCols) {
    if (widgets == null) {
      return;
    }
    Set<String> ids = new HashSet<String>();
    for (int i = 0; i < widgets.size(); i++) {
      DashboardBusinessModelMeta.WidgetDef widget = widgets.get(i);
      if (widget == null) {
        context.addError(new BusinessDefineFeatureError("dashboard.widgets[" + i + "]-required"));
        continue;
      }
      if (isBlank(widget.getId())) {
        context.addError(new BusinessDefineFeatureError("dashboard.widgets[" + i + "].id-required"));
      } else if (!ids.add(widget.getId())) {
        context.addError(new BusinessDefineFeatureError("dashboard.widgets[" + i + "].id-duplicate"));
      }
      if (isBlank(widget.getWidgetName()) || !ALLOWED_WIDGET_TYPES.contains(widget.getWidgetName())) {
        context.addError(new BusinessDefineFeatureError("dashboard.widgets[" + i + "].widgetName-invalid"));
      }
      if (isBlank(widget.getChartName())) {
        context.addError(new BusinessDefineFeatureError("dashboard.widgets[" + i + "].chartName-required"));
      }
      validatePosition(context, i, widget.getPosition());
      validateSize(context, i, widget.getSize());
      validateBounds(context, i, widget, layoutCols);
    }
    validateOverlaps(context, widgets);
  }

  private Integer layoutCols(DashboardBusinessModelMeta.LayoutConfig layout) {
    return layout == null ? null : layout.getCols();
  }

  private void validatePosition(ValidationContext<DashboardBusinessModel> context,
                                int index,
                                DashboardBusinessModelMeta.Position position) {
    if (position == null) {
      context.addError(new BusinessDefineFeatureError("dashboard.widgets[" + index + "].position-required"));
      return;
    }
    if (position.getX() == null || position.getX().intValue() < 0) {
      context.addError(new BusinessDefineFeatureError("dashboard.widgets[" + index + "].position.x-invalid"));
    }
    if (position.getY() == null || position.getY().intValue() < 0) {
      context.addError(new BusinessDefineFeatureError("dashboard.widgets[" + index + "].position.y-invalid"));
    }
  }

  private void validateSize(ValidationContext<DashboardBusinessModel> context,
                            int index,
                            DashboardBusinessModelMeta.Size size) {
    if (size == null) {
      context.addError(new BusinessDefineFeatureError("dashboard.widgets[" + index + "].size-required"));
      return;
    }
    if (size.getCols() == null || size.getCols().intValue() <= 0) {
      context.addError(new BusinessDefineFeatureError("dashboard.widgets[" + index + "].size.cols-invalid"));
    }
    if (size.getRows() == null || size.getRows().intValue() <= 0) {
      context.addError(new BusinessDefineFeatureError("dashboard.widgets[" + index + "].size.rows-invalid"));
    }
    if (size.getCols() != null && (size.getCols().intValue() < MIN_WIDGET_COLS
        || size.getCols().intValue() > MAX_WIDGET_COLS)) {
      context.addError(new BusinessDefineFeatureError("dashboard.widgets[" + index + "].size.cols-out-of-range"));
    }
    if (size.getRows() != null && (size.getRows().intValue() < MIN_WIDGET_ROWS
        || size.getRows().intValue() > MAX_WIDGET_ROWS)) {
      context.addError(new BusinessDefineFeatureError("dashboard.widgets[" + index + "].size.rows-out-of-range"));
    }
  }

  private void validateBounds(ValidationContext<DashboardBusinessModel> context,
                              int index,
                              DashboardBusinessModelMeta.WidgetDef widget,
                              Integer layoutCols) {
    if (layoutCols == null || widget.getPosition() == null || widget.getSize() == null
        || widget.getPosition().getX() == null || widget.getSize().getCols() == null
        || widget.getPosition().getX().intValue() < 0 || widget.getSize().getCols().intValue() <= 0) {
      return;
    }
    long rightEdge = (long) widget.getPosition().getX().intValue() + widget.getSize().getCols().intValue();
    if (rightEdge > layoutCols.intValue()) {
      context.addError(new BusinessDefineFeatureError("dashboard.widgets[" + index + "].bounds-invalid"));
    }
  }

  private void validateOverlaps(ValidationContext<DashboardBusinessModel> context,
                                List<DashboardBusinessModelMeta.WidgetDef> widgets) {
    if (widgets == null) {
      return;
    }
    for (int first = 0; first < widgets.size(); first++) {
      DashboardBusinessModelMeta.WidgetDef left = widgets.get(first);
      if (!hasValidRectangle(left)) {
        continue;
      }
      for (int second = first + 1; second < widgets.size(); second++) {
        DashboardBusinessModelMeta.WidgetDef right = widgets.get(second);
        if (hasValidRectangle(right) && overlaps(left, right)) {
          context.addError(new BusinessDefineFeatureError("dashboard.widgets[" + first + "].overlaps[" + second + "]"));
        }
      }
    }
  }

  private boolean hasValidRectangle(DashboardBusinessModelMeta.WidgetDef widget) {
    return widget != null && widget.getPosition() != null && widget.getSize() != null
        && widget.getPosition().getX() != null && widget.getPosition().getY() != null
        && widget.getSize().getCols() != null && widget.getSize().getRows() != null
        && widget.getPosition().getX().intValue() >= 0 && widget.getPosition().getY().intValue() >= 0
        && widget.getSize().getCols().intValue() > 0 && widget.getSize().getRows().intValue() > 0;
  }

  private boolean overlaps(DashboardBusinessModelMeta.WidgetDef left,
                           DashboardBusinessModelMeta.WidgetDef right) {
    int leftX = left.getPosition().getX().intValue();
    int leftY = left.getPosition().getY().intValue();
    int rightX = right.getPosition().getX().intValue();
    int rightY = right.getPosition().getY().intValue();
    return leftX < rightX + right.getSize().getCols().intValue()
        && leftX + left.getSize().getCols().intValue() > rightX
        && leftY < rightY + right.getSize().getRows().intValue()
        && leftY + left.getSize().getRows().intValue() > rightY;
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}

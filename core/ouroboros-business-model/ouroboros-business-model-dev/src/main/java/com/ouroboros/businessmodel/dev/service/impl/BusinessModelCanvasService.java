package com.ouroboros.businessmodel.dev.service.impl;

import org.springframework.stereotype.Service;

import com.ouroboros.businessmodel.dev.vo.BusinessModelCanvas;
import com.ouroboros.businessmodel.dev.vo.BusinessModelCanvas.Layout;
import com.ouroboros.businessmodel.dev.vo.BusinessModelCanvas.Point;
import com.ouroboros.dev.collaboration.CollaborativeResource;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.util.Json;

@Service
public class BusinessModelCanvasService implements CollaborativeResource {
  private static final String DIRECTORY = "business-model/canvases";
  @Override public String collaborationType() { return "business-model-canvas"; }
  @Override public String collaborationDirectory() { return DIRECTORY; }
  @Override public String collaborationSuffix() { return ".canvas.json"; }
  @Override public void validateCollaborationSeed(String id, String json) {
    validate(id, Json.toBean(BusinessModelCanvas.class, json));
  }

  private void validate(String id, BusinessModelCanvas canvas) {
    if (id == null || !id.matches("overview|[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}") || canvas == null || !id.equals(canvas.id())) {
      throw new ValidationException("画布标识无效");
    }
    if (canvas.title() == null || canvas.title().isBlank() || canvas.title().length() > 80
        || canvas.modelIds() == null || canvas.modelIds().stream().anyMatch(name -> name == null || name.isBlank())
        || canvas.allModels() != "overview".equals(id)) {
      throw new ValidationException("画布名称或模型选择无效");
    }
    validateLayout(canvas.freeLayout());
    validateLayout(canvas.groupedLayout());
  }

  private void validateLayout(Layout layout) {
    if (layout == null || layout.nodes() == null || layout.groups() == null) {
      throw new ValidationException("画布布局不能为空");
    }
    layout.nodes().values().forEach(this::validatePoint);
    layout.groups().values().forEach(group -> {
      if (group == null || !Double.isFinite(group.width()) || !Double.isFinite(group.height())
          || group.width() <= 0 || group.height() <= 0) {
        throw new ValidationException("模块框尺寸无效");
      }
      validatePoint(new Point(group.x(), group.y()));
    });
    if (layout.viewport() != null) {
      var zoom = layout.viewport().zoom();
      if (!Double.isFinite(zoom) || zoom <= 0 || zoom > 4) {
        throw new ValidationException("画布缩放比例无效");
      }
      validatePoint(layout.viewport().offset());
    }
  }

  private void validatePoint(Point point) {
    if (point == null || !Double.isFinite(point.x()) || !Double.isFinite(point.y())) {
      throw new ValidationException("画布坐标无效");
    }
  }
}

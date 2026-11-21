package com.ouroboros.businessmodel.meta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.ouroboros.businessmodel.BusinessModelTypes;

public class DashboardBusinessModelMeta extends BusinessModelMeta {
  private LayoutConfig layout = new LayoutConfig();
  private List<WidgetDef> widgets = new ArrayList<WidgetDef>();

  public DashboardBusinessModelMeta() {
    setModelType(BusinessModelTypes.DASHBOARD);
  }

  @Override
  public void setExtraProp(String key, Object value) {
    if ("fullName".equals(key)) {
      return;
    }
    throw new IllegalArgumentException("unknown dashboard metadata property: " + key);
  }

  public LayoutConfig getLayout() {
    return layout;
  }

  public void setLayout(LayoutConfig layout) {
    this.layout = layout;
  }

  public List<WidgetDef> getWidgets() {
    return widgets;
  }

  public void setWidgets(List<WidgetDef> widgets) {
    this.widgets = widgets;
  }

  public static class LayoutConfig {
    private Integer cols = Integer.valueOf(24);
    private Integer rowHeight = Integer.valueOf(30);
    private Boolean draggable = Boolean.TRUE;
    private Boolean resizable = Boolean.TRUE;

    public Integer getCols() {
      return cols;
    }

    public void setCols(Integer cols) {
      this.cols = cols;
    }

    public Integer getRowHeight() {
      return rowHeight;
    }

    public void setRowHeight(Integer rowHeight) {
      this.rowHeight = rowHeight;
    }

    public Boolean getDraggable() {
      return draggable;
    }

    public void setDraggable(Boolean draggable) {
      this.draggable = draggable;
    }

    public Boolean getResizable() {
      return resizable;
    }

    public void setResizable(Boolean resizable) {
      this.resizable = resizable;
    }
  }

  public static class WidgetDef {
    private String id;
    private String widgetName;
    private String chartName;
    private Position position = new Position();
    private Size size = new Size();
    private WidgetConfig config = new WidgetConfig();

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getWidgetName() {
      return widgetName;
    }

    public void setWidgetName(String widgetName) {
      this.widgetName = widgetName;
    }

    public String getChartName() {
      return chartName;
    }

    public void setChartName(String chartName) {
      this.chartName = chartName;
    }

    public Position getPosition() {
      return position;
    }

    public void setPosition(Position position) {
      this.position = position;
    }

    public Size getSize() {
      return size;
    }

    public void setSize(Size size) {
      this.size = size;
    }

    public WidgetConfig getConfig() {
      return config;
    }

    public void setConfig(WidgetConfig config) {
      this.config = config;
    }
  }

  public static class Position {
    private Integer x = Integer.valueOf(0);
    private Integer y = Integer.valueOf(0);

    public Integer getX() {
      return x;
    }

    public void setX(Integer x) {
      this.x = x;
    }

    public Integer getY() {
      return y;
    }

    public void setY(Integer y) {
      this.y = y;
    }
  }

  public static class Size {
    private Integer cols = Integer.valueOf(12);
    private Integer rows = Integer.valueOf(10);

    public Integer getCols() {
      return cols;
    }

    public void setCols(Integer cols) {
      this.cols = cols;
    }

    public Integer getRows() {
      return rows;
    }

    public void setRows(Integer rows) {
      this.rows = rows;
    }
  }

  public static class WidgetConfig {
    private String title;
    private Boolean showHeader = Boolean.TRUE;
    private Boolean showBorder = Boolean.TRUE;
    private Map<String, String> border;
    private Boolean showRadius;
    private Map<String, String> borderRadius;
    private Boolean showShadow;
    private String boxShadow;
    private String bodyPadding;
    private String backgroundColor;
    private String headerBackgroundColor;

    public String getTitle() {
      return title;
    }

    public void setTitle(String title) {
      this.title = title;
    }

    public Boolean getShowHeader() {
      return showHeader;
    }

    public void setShowHeader(Boolean showHeader) {
      this.showHeader = showHeader;
    }

    public Boolean getShowBorder() {
      return showBorder;
    }

    public void setShowBorder(Boolean showBorder) {
      this.showBorder = showBorder;
    }

    public Map<String, String> getBorder() {
      return border;
    }

    public void setBorder(Map<String, String> border) {
      this.border = border;
    }

    public Boolean getShowRadius() {
      return showRadius;
    }

    public void setShowRadius(Boolean showRadius) {
      this.showRadius = showRadius;
    }

    public Map<String, String> getBorderRadius() {
      return borderRadius;
    }

    public void setBorderRadius(Map<String, String> borderRadius) {
      this.borderRadius = borderRadius;
    }

    public Boolean getShowShadow() {
      return showShadow;
    }

    public void setShowShadow(Boolean showShadow) {
      this.showShadow = showShadow;
    }

    public String getBoxShadow() {
      return boxShadow;
    }

    public void setBoxShadow(String boxShadow) {
      this.boxShadow = boxShadow;
    }

    public String getBodyPadding() {
      return bodyPadding;
    }

    public void setBodyPadding(String bodyPadding) {
      this.bodyPadding = bodyPadding;
    }

    public String getBackgroundColor() {
      return backgroundColor;
    }

    public void setBackgroundColor(String backgroundColor) {
      this.backgroundColor = backgroundColor;
    }

    public String getHeaderBackgroundColor() {
      return headerBackgroundColor;
    }

    public void setHeaderBackgroundColor(String headerBackgroundColor) {
      this.headerBackgroundColor = headerBackgroundColor;
    }
  }
}

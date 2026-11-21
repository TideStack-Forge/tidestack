package com.ouroboros.businessmodel.meta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ouroboros.businessmodel.BusinessModelTypes;

public class CompositeViewBusinessModelMeta extends BusinessModelMeta {
  private String datasetName;
  private Object embeddedQuery;
  private List<ColumnDef> columns = new ArrayList<ColumnDef>();
  private FilterConfig filterConfig = new FilterConfig();
  private List<ActionButtonDef> actionButtons = new ArrayList<ActionButtonDef>();
  private LayoutConfig layoutConfig = new LayoutConfig();

  public CompositeViewBusinessModelMeta() {
    setModelType(BusinessModelTypes.COMPOSITE_VIEW);
  }

  public String getDatasetName() {
    return datasetName;
  }

  public void setDatasetName(String datasetName) {
    this.datasetName = datasetName;
  }

  public Object getEmbeddedQuery() {
    return embeddedQuery;
  }

  public void setEmbeddedQuery(Object embeddedQuery) {
    this.embeddedQuery = embeddedQuery;
  }

  public List<ColumnDef> getColumns() {
    return columns;
  }

  public void setColumns(List<ColumnDef> columns) {
    this.columns = columns;
  }

  public FilterConfig getFilterConfig() {
    return filterConfig;
  }

  public void setFilterConfig(FilterConfig filterConfig) {
    this.filterConfig = filterConfig;
  }

  public List<ActionButtonDef> getActionButtons() {
    return actionButtons;
  }

  public void setActionButtons(List<ActionButtonDef> actionButtons) {
    this.actionButtons = actionButtons;
  }

  public LayoutConfig getLayoutConfig() {
    return layoutConfig;
  }

  public void setLayoutConfig(LayoutConfig layoutConfig) {
    this.layoutConfig = layoutConfig;
  }

  public static class ColumnDef {
    private String field;
    private String title;
    private Integer width;
    private String format;

    public String getField() {
      return field;
    }

    public void setField(String field) {
      this.field = field;
    }

    public String getTitle() {
      return title;
    }

    public void setTitle(String title) {
      this.title = title;
    }

    public Integer getWidth() {
      return width;
    }

    public void setWidth(Integer width) {
      this.width = width;
    }

    public String getFormat() {
      return format;
    }

    public void setFormat(String format) {
      this.format = format;
    }
  }

  public static class FilterConfig {
    private List<FilterItemDef> items = new ArrayList<FilterItemDef>();

    public List<FilterItemDef> getItems() {
      return items;
    }

    public void setItems(List<FilterItemDef> items) {
      this.items = items;
    }
  }

  public static class FilterItemDef {
    private String field;
    private List<String> ops = new ArrayList<String>();
    private String widget;

    public String getField() {
      return field;
    }

    public void setField(String field) {
      this.field = field;
    }

    public List<String> getOps() {
      return ops;
    }

    public void setOps(List<String> ops) {
      this.ops = ops;
    }

    public String getWidget() {
      return widget;
    }

    public void setWidget(String widget) {
      this.widget = widget;
    }
  }

  public static class ActionButtonDef {
    private String label;
    private String icon;
    private String scope;
    private String apiRef;
    private Map<String, String> contextBinding = new LinkedHashMap<String, String>();

    public String getLabel() {
      return label;
    }

    public void setLabel(String label) {
      this.label = label;
    }

    public String getIcon() {
      return icon;
    }

    public void setIcon(String icon) {
      this.icon = icon;
    }

    public String getScope() {
      return scope;
    }

    public void setScope(String scope) {
      this.scope = scope;
    }

    public String getApiRef() {
      return apiRef;
    }

    public void setApiRef(String apiRef) {
      this.apiRef = apiRef;
    }

    public Map<String, String> getContextBinding() {
      return contextBinding;
    }

    public void setContextBinding(Map<String, String> contextBinding) {
      this.contextBinding = contextBinding;
    }
  }

  public static class LayoutConfig {
    private Integer pageSize;
    private List<SortDef> defaultSort = new ArrayList<SortDef>();

    public Integer getPageSize() {
      return pageSize;
    }

    public void setPageSize(Integer pageSize) {
      this.pageSize = pageSize;
    }

    public List<SortDef> getDefaultSort() {
      return defaultSort;
    }

    public void setDefaultSort(List<SortDef> defaultSort) {
      this.defaultSort = defaultSort;
    }
  }

  public static class SortDef {
    private String field;
    private String direction;

    public String getField() {
      return field;
    }

    public void setField(String field) {
      this.field = field;
    }

    public String getDirection() {
      return direction;
    }

    public void setDirection(String direction) {
      this.direction = direction;
    }
  }
}

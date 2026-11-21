package com.ouroboros.businessmodel.schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ouroboros.businessmodel.BusinessModelProvider;
import com.ouroboros.businessmodel.BusinessModelCenter;

import com.ouroboros.businessmodel.meta.CompositeViewBusinessModelMeta;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.util.Json;
import com.ouroboros.util.Maps;
import com.ouroboros.webapi.WebAPIModel;

/** Builds the read UI from the same composite-view contract enforced by its fixed API. */
public final class CompositeViewRuntimeSchema {
  private CompositeViewRuntimeSchema() {
  }

  public static SchemaNode build(CompositeViewBusinessModelMeta view) {
    return build(view, BusinessModelCenter.buildInProvider);
  }

  public static SchemaNode build(CompositeViewBusinessModelMeta view, BusinessModelProvider provider) {
    Map<String, Object> api = new LinkedHashMap<>();
    api.put("method", "post");
    api.put("url", "/api/runtime/composite-view/" + view.getFullName() + "/execute");
    api.put("requestAdaptor", requestAdaptor(view));
    api.put("adaptor", "const result = payload.data || {}; return {...payload, data: {items: result.rows || [], total: result.pageInfo?.total || 0}};");
    Map<String, Object> crud = new LinkedHashMap<>();
    crud.put("type", "crud");
    crud.put("api", api);
    crud.put("perPage", view.getLayoutConfig().getPageSize());
    crud.put("columns", columns(view, provider));
    crud.put("filter", Map.of("body", filters(view), "actions", List.of(Map.of("type", "submit", "label", "查询"))));
    crud.put("bulkActions", actions(view, "batch", provider));
    SchemaNode page = new SchemaNode();
    page.setType("page");
    page.setProp("title", view.getTitle());
    page.setProp("body", crud);
    return page;
  }

  private static List<Map<String, Object>> columns(CompositeViewBusinessModelMeta view, BusinessModelProvider provider) {
    List<Map<String, Object>> columns = new ArrayList<>();
    var source = com.ouroboros.util.SPI.getCachedReversedServiceStream(
        com.ouroboros.businessmodel.validation.CompositeViewSourceSchemaProvider.class)
        .filter(com.ouroboros.businessmodel.validation.CompositeViewSourceSchemaProvider::supportsCurrentContext).findFirst()
        .flatMap(providerSource -> view.getDatasetName() == null || view.getDatasetName().isBlank()
            ? providerSource.resolveEmbedded(view.getEmbeddedQuery(), view.getFullName()) : providerSource.resolve(view.getDatasetName()));
    for (var column : view.getColumns()) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("name", column.getField());
      item.put("label", column.getTitle());
      item.put("sortable", source.map(schema -> schema.fields().stream().anyMatch(field ->
          field.name().equalsIgnoreCase(column.getField()) && field.sortable())).orElse(false));
      String format = column.getFormat() == null ? "text" : column.getFormat();
      switch (format) {
        case "number", "integer", "decimal", "currency" -> item.put("type", "number");
        case "percent" -> {
          item.put("type", "tpl");
          item.put("tpl", "${" + column.getField() + "}%");
        }
        case "date", "datetime" -> item.put("type", format);
        case "boolean" -> item.put("type", "status");
        default -> item.put("type", "text");
      }
      if (column.getWidth() != null) {
        item.put("width", column.getWidth());
      }
      columns.add(item);
    }
    List<Map<String, Object>> rowActions = actions(view, "row", provider);
    if (!rowActions.isEmpty()) {
      columns.add(Map.of("type", "operation", "label", "操作", "buttons", rowActions));
    }
    return columns;
  }

  private static List<Map<String, Object>> filters(CompositeViewBusinessModelMeta view) {
    List<Map<String, Object>> filters = new ArrayList<>();
    if (view.getFilterConfig() == null) {
      return filters;
    }
    if (view.getFilterConfig().getItems() == null) {
      return filters;
    }
    for (var item : view.getFilterConfig().getItems()) {
      String prefix = "filters." + item.getField();
      filters.add(Map.of("type", "select", "name", prefix + ".op", "label", item.getField() + " 条件",
          "value", item.getOps().getFirst(), "options", item.getOps().stream()
              .map(op -> Map.of("label", op, "value", op)).toList()));
      String widget = switch (item.getWidget()) {
        case "number" -> "input-number";
        case "date" -> "input-date";
        case "datetime" -> "input-datetime";
        case "checkbox", "switch" -> "switch";
        case "multiSelect" -> "input-tag";
        default -> "input-text";
      };
      String multiple = "['in','notIn'].includes(filters?.[" + Json.toJsonString(item.getField()) + "]?.op)";
      String nullary = "['isNull','isNotNull'].includes(filters?.[" + Json.toJsonString(item.getField()) + "]?.op)";
      filters.add(Map.of("type", widget, "name", prefix + ".value", "label", item.getField(),
          "visibleOn", "${!(" + multiple + ") && !(" + nullary + ")}"));
      filters.add(Map.of("type", "input-array", "name", prefix + ".value", "label", item.getField(),
          "items", Map.of("type", widget), "visibleOn", "${" + multiple + "}"));
    }
    return filters;
  }

  private static String requestAdaptor(CompositeViewBusinessModelMeta view) {
    List<String> fields = view.getFilterConfig() == null || view.getFilterConfig().getItems() == null ? List.of()
        : view.getFilterConfig().getItems().stream().map(CompositeViewBusinessModelMeta.FilterItemDef::getField).toList();
    return "const fields = " + Json.toJsonString(fields) + ";\n" + "const data = api.data || {};\n"
        + "const children = fields.flatMap(field => { const item = data.filters?.[field]; "
        + "if (!item || (!['isNull', 'isNotNull'].includes(item.op) && (item.value == null || item.value === ''))) return []; "
        + "return [{field, op: item.op, value: item.value, values: Array.isArray(item.value) ? item.value : []}]; });\n"
        + "return {...api, data: {page: {pageNo: Number(data.page) || 1, pageSize: Number(data.perPage) || "
        + view.getLayoutConfig().getPageSize() + "}, dynamicFilter: {filter: children.length ? {op:'and', children} : null, "
        + "sort: data.orderBy ? [{field:data.orderBy, direction:data.orderDir || 'asc'}] : []}}};";
  }

  private static List<Map<String, Object>> actions(CompositeViewBusinessModelMeta view, String scope, BusinessModelProvider provider) {
    List<Map<String, Object>> buttons = new ArrayList<>();
    if (view.getActionButtons() == null) {
      return buttons;
    }
    for (var action : view.getActionButtons()) {
      if (!scope.equals(action.getScope())) {
        continue;
      }
      WebAPIModel api = CompositeViewWebApis.resolve(provider, action.getApiRef());
      Map<String, Object> binding = new LinkedHashMap<>();
      action.getContextBinding().forEach((target, source) -> Maps.Hierarchical.setValue(binding, target,
          bindingExpression(source, scope, view)));
      buttons.add(Map.of("type", "button", "label", action.getLabel(), "actionType", "ajax",
          "api", Map.of("method", api.getMethod(), "url", actionPath(com.ouroboros.webapi.WebAPIRequestPathResolver.requestPath(api), action, scope, view), "data", binding)));
    }
    return buttons;
  }
  private static String actionPath(String path, CompositeViewBusinessModelMeta.ActionButtonDef action,
                                   String scope, CompositeViewBusinessModelMeta view) {
    return java.util.regex.Pattern.compile("\\{([^}:]+)(?::[^}]+)?\\}").matcher(path)
        .replaceAll(match -> java.util.regex.Matcher.quoteReplacement(bindingExpression(
            action.getContextBinding().getOrDefault(match.group(1), match.group(1)), scope, view)));
  }

  private static String bindingExpression(String source, String scope, CompositeViewBusinessModelMeta view) {
    String field = source.trim();
    if (field.startsWith("${") && field.endsWith("}")) {
      field = field.substring(2, field.length() - 1).trim();
    }
    String reference = field;
    field = view.getColumns().stream().map(CompositeViewBusinessModelMeta.ColumnDef::getField)
        .filter(name -> name.equalsIgnoreCase(reference)).findFirst().orElse(reference);
    return "batch".equals(scope)
        ? "${" + ("selectedItems".equals(field) ? field : "selectedItems|pick:" + field) + "}"
        : "${" + field + "}";
  }
}

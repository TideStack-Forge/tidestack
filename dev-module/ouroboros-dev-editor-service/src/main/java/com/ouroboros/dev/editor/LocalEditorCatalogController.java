package com.ouroboros.dev.editor;

import java.util.*;
import com.ouroboros.devhelper.command.Commands;
import com.ouroboros.fieldtype.FieldTypeDefinition;
import com.ouroboros.util.Json;
import com.ouroboros.web.*;
import com.ouroboros.uischema.schema.CommonContainer;
import org.springframework.web.bind.annotation.*;

@RestController
public final class LocalEditorCatalogController {
  private final LocalHelperConnection helper;
  public LocalEditorCatalogController(LocalHelperConnection helper) { this.helper = helper; }
  @GetMapping("/api/dev/{appName}/logicflow/node-define")
  public WebApiResult<Object> nodes() { return WebApiResult.ok(helper.execute(Commands.EDITOR_CATALOG.getCommand(), Map.of("kind", "logicflow")).getData()); }
  @GetMapping("/api/app/{appName}/field-types/{kind:primary|normal}")
  public ItemsWebApiResult<SelectOption> fieldTypes(@PathVariable String kind) {
    return ItemsWebApiResult.ok(fields().stream().filter(field -> Boolean.TRUE.equals(field.getIsPrimaryKeyType()) == kind.equals("primary"))
        .sorted(Comparator.comparingInt(field -> Optional.ofNullable(field.getOrder()).orElse(Integer.MAX_VALUE)))
        .map(field -> new SelectOption(field.getName(), field.getLabel())).toList());
  }
  @GetMapping("/api/app/{appName}/field-types/{name}/ui-schema")
  public WebApiResult<CommonContainer> fieldSchema(@PathVariable String name) {
    var field = fields().stream().filter(candidate -> name.equals(candidate.getName())).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown field type"));
    var container = new CommonContainer(); container.setType("container"); container.setBody(field.getParamsUiSchema());
    return WebApiResult.ok(container);
  }
  @GetMapping("/api/dev/{appName}/approval-workflow/candidate")
  public WebApiResult<Object> candidateTypes() { return candidates(Map.of("operation", "types")); }
  @GetMapping("/api/dev/{appName}/approval-workflow/candidate/{type}/{operation:items|category}")
  public WebApiResult<Object> candidateItems(@PathVariable String type, @PathVariable String operation,
      @RequestParam(required = false) String category, @RequestParam(required = false) String keyword,
      @RequestParam(required = false, name = "value[]") List<String> values,
      @RequestParam(required = false, name = "value") List<String> value) {
    var params = new HashMap<String, Object>(); params.put("operation", operation); params.put("type", type);
    if (category != null) params.put("category", category);
    if (keyword != null) params.put("keyword", keyword);
    if (values != null || value != null) params.put("values", values != null ? values : value);
    return candidates(params);
  }
  private WebApiResult<Object> candidates(Map<String, Object> params) {
    return WebApiResult.ok(Map.of("items", helper.execute(Commands.APPROVAL_CANDIDATES.getCommand(), params).getData()));
  }
  private List<FieldTypeDefinition> fields() {
    var data = helper.execute(Commands.EDITOR_CATALOG.getCommand(), Map.of("kind", "field-types")).getData();
    return Arrays.asList(Json.toBean(FieldTypeDefinition[].class, Json.toJsonString(data)));
  }
}

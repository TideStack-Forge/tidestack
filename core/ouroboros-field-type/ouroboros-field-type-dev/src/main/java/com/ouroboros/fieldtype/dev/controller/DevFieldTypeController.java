package com.ouroboros.fieldtype.dev.controller;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import jakarta.annotation.security.PermitAll;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.fieldtype.FieldTypeCatalog;
import com.ouroboros.fieldtype.FieldTypeCatalogContext;
import com.ouroboros.fieldtype.FieldTypeDefinition;
import com.ouroboros.uischema.schema.CommonContainer;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.SelectOption;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/app/{appName}/field-types")
public class DevFieldTypeController {
  private final FieldTypeCatalog fieldTypeCatalog;

  @Autowired
  public DevFieldTypeController(FieldTypeCatalog fieldTypeCatalog) {
    this.fieldTypeCatalog = fieldTypeCatalog;
  }

  private List<SelectOption> getFieldTypeOptions(String appName, Predicate<FieldTypeDefinition> filter) {
    var allDefinitions = fieldTypeCatalog.listMetadata(FieldTypeCatalogContext.devApp(appName));
    return allDefinitions.stream()
        .filter(filter)
        .sorted(Comparator.comparingInt(definition -> Optional.ofNullable(definition.getOrder()).orElse(Integer.MAX_VALUE)))
        .map(definition -> new SelectOption(definition.getName(), definition.getLabel()))
        .collect(Collectors.toList());
  }

  @GetMapping("/normal")
  @PermitAll
  public ItemsWebApiResult<SelectOption> getNormalFieldTypes(@PathVariable("appName") String appName) {
    var options = getFieldTypeOptions(appName, field -> !Boolean.TRUE.equals(field.getIsPrimaryKeyType()));
    return ItemsWebApiResult.ok(options);
  }

  @GetMapping("/primary")
  @PermitAll
  public ItemsWebApiResult<SelectOption> getPrimaryFieldTypes(@PathVariable("appName") String appName) {
    var options = getFieldTypeOptions(appName, field -> Boolean.TRUE.equals(field.getIsPrimaryKeyType()));
    return ItemsWebApiResult.ok(options);
  }

  @GetMapping("/{fieldName}/ui-schema")
  @PermitAll
  public WebApiResult<SchemaNode> getUiSchema(
      @PathVariable("appName") String appName,
      @PathVariable("fieldName") String fieldName) {
    var fieldTypeDefinition = fieldTypeCatalog.getMetadata(FieldTypeCatalogContext.devApp(appName), fieldName);
    var container = new CommonContainer();
    container.setType("container");
    if (!fieldTypeDefinition.isPresent()) {
      return WebApiResult.ok(container);
    }
    container.setBody(fieldTypeDefinition.get().getParamsUiSchema());
    return WebApiResult.ok(container);
  }
}

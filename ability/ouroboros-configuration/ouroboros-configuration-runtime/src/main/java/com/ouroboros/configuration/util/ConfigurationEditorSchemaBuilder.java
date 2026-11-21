package com.ouroboros.configuration.util;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.ouroboros.configuration.metadata.ConfigValueType;
import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.uischema.schema.FormItemNode;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.util.Converter;

@Component
public class ConfigurationEditorSchemaBuilder {

  public SchemaNode build(ConfigurationMetadata metadata) {
    var schema = Optional.ofNullable(metadata)
        .map(ConfigurationMetadata::getEditorSchema);
    return schema
        .filter(s -> StringUtils.isNotBlank(s.getType()))
        .orElseGet(() -> buildSchemaFromValueType(metadata));
  }

  private SchemaNode buildSchemaFromValueType(ConfigurationMetadata metadata) {
    var valueType = Optional.ofNullable(metadata)
        .map(ConfigurationMetadata::getValueType)
        .orElse(ConfigValueType.STRING);
    var required = !Optional.ofNullable(metadata)
        .map(ConfigurationMetadata::isNullable)
        .orElse(false);
    var modifiable = Optional.ofNullable(metadata)
        .map(ConfigurationMetadata::isModifiable)
        .orElse(true);

    var schema = new FormItemNode();
    var extraMap = Optional.ofNullable(metadata)
        .map(ConfigurationMetadata::getExtraProps)
        .orElseGet(Collections::emptyMap);
    extraMap.forEach(schema::setProp);

    schema.setRequired(required);
    schema.setStatic(!modifiable);
    var options = Optional.ofNullable(metadata)
        .flatMap(m -> m.getExtraProp("options"))
        .filter(Collection.class::isInstance)
        .map(Collection.class::cast)
        .orElse(Collections.emptyList());
    if (!options.isEmpty()) {
      schema.setType("select");
      schema.setProp("options", options);
      return schema;
    }

    switch (valueType) {
      case STRING:
        schema.setType("input-text");
        break;
      case LONG:
        schema.setProp("big", true);
      case INT:
        schema.setType("input-number");
        schema.setProp("precision", 0);
        break;
      case DOUBLE:
        schema.setProp("big", true);
      case FLOAT:
        schema.setType("input-number");
        var precision = Optional.ofNullable(metadata)
            .flatMap(m -> m.getExtraProp("precision"))
            .map(Converter::toInteger)
            .orElse(2);
        schema.setProp("precision", precision);
        break;
      case BOOLEAN:
        schema.setType("switch");
        break;
      case MAP:
      case OBJECT:
        schema.setType("input-kv");
        break;
      case LIST:
      case ARRAY:
        schema.setType("json-editor");
        break;
      default:
        schema.setType("textarea");
    }

    return schema;
  }
}

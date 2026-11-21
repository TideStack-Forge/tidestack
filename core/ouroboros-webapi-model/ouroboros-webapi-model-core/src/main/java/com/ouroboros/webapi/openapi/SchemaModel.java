package com.ouroboros.webapi.openapi;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ouroboros.model.AbstractExtraPropsModel;
import com.ouroboros.model.Model;

/**
 * OpenAPI 3.1 Schema 模型
 * 支持 JSON Schema 2020-12 常用关键字，并兼容现有 OpenAPI 3.0 字段。
 */
public class SchemaModel extends AbstractExtraPropsModel<SchemaModel, SchemaModel.Builder> implements Model<SchemaModel> {

  /**
   * Schema 类型（string, number, integer, boolean, array, object）
   */
  @JsonIgnore
  private String type;

  /**
   * OpenAPI 3.1 / JSON Schema 联合类型
   */
  @JsonIgnore
  private List<String> types;

  /**
   * 格式（date, date-time, email, uuid, etc.）
   */
  private String format;

  /**
   * Schema 标题
   */
  private String title;

  /**
   * Schema 描述
   */
  private String description;

  /**
   * 默认值
   */
  @JsonProperty("default")
  private Object defaultValue;

  /**
   * 是否可为 null
   */
  private Boolean nullable;

  /**
   * 只读
   */
  private Boolean readOnly;

  /**
   * 只写
   */
  private Boolean writeOnly;

  /**
   * 示例值
   */
  private Object example;

  /**
   * 是否废弃
   */
  private Boolean deprecated;

  /**
   * 枚举值
   */
  @JsonProperty("enum")
  private List<Object> enumValues;

  /**
   * 对象属性（当 type 为 object 时）
   */
  private Map<String, SchemaModel> properties;

  /**
   * 必填属性列表
   */
  private List<String> required;

  /**
   * 数组项 Schema（当 type 为 array 时）
   */
  private SchemaModel items;

  /**
   * 最小值
   */
  private Number minimum;

  /**
   * 最大值
   */
  private Number maximum;

  /**
   * 最小长度
   */
  private Integer minLength;

  /**
   * 最大长度
   */
  private Integer maxLength;

  /**
   * 正则表达式模式
   */
  private String pattern;

  /**
   * 最小数组项数
   */
  private Integer minItems;

  /**
   * 最大数组项数
   */
  private Integer maxItems;

  /**
   * 数组元素是否唯一
   */
  private Boolean uniqueItems;

  /**
   * oneOf 组合
   */
  private List<SchemaModel> oneOf;

  /**
   * anyOf 组合
   */
  private List<SchemaModel> anyOf;

  /**
   * allOf 组合
   */
  private List<SchemaModel> allOf;

  /**
   * not 组合
   */
  private SchemaModel not;

  /**
   * 对象额外属性定义，可为 boolean 或 Schema
   */
  private Object additionalProperties;

  /**
   * 多倍数约束
   */
  private Number multipleOf;

  /**
   * 独占最小值约束
   */
  private Number exclusiveMinimum;

  /**
   * 独占最大值约束
   */
  private Number exclusiveMaximum;

  /**
   * 最小属性数
   */
  private Integer minProperties;

  /**
   * 最大属性数
   */
  private Integer maxProperties;

  /**
   * 常量值约束
   */
  @JsonProperty("const")
  private Object constValue;

  /**
   * 引用其他 Schema（$ref）
   */
  @JsonProperty("$ref")
  private String ref;

  public static Builder builder() {
    return new Builder();
  }

  public String getType() {
    return type;
  }

  public List<String> getTypes() {
    return types;
  }

  @JsonProperty("type")
  private Object getJsonType() {
    return types != null ? types : type;
  }

  @JsonProperty("type")
  private void setJsonType(Object jsonType) {
    if (jsonType instanceof List) {
      // OpenAPI 3.1 allows "type" to be a string array for union types.
      this.types = ((List<?>) jsonType).stream()
          .map(String::valueOf)
          .collect(Collectors.toList());
      this.type = null;
      return;
    }

    this.type = jsonType != null ? String.valueOf(jsonType) : null;
    this.types = null;
  }

  public String getFormat() {
    return format;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public Object getDefaultValue() {
    return defaultValue;
  }

  public Boolean getNullable() {
    return nullable;
  }

  public Boolean getReadOnly() {
    return readOnly;
  }

  public Boolean getWriteOnly() {
    return writeOnly;
  }

  public Object getExample() {
    return example;
  }

  public Boolean getDeprecated() {
    return deprecated;
  }

  public List<Object> getEnumValues() {
    return enumValues;
  }

  public Map<String, SchemaModel> getProperties() {
    return properties;
  }

  public List<String> getRequired() {
    return required;
  }

  public SchemaModel getItems() {
    return items;
  }

  public Number getMinimum() {
    return minimum;
  }

  public Number getMaximum() {
    return maximum;
  }

  public Integer getMinLength() {
    return minLength;
  }

  public Integer getMaxLength() {
    return maxLength;
  }

  public String getPattern() {
    return pattern;
  }

  public Integer getMinItems() {
    return minItems;
  }

  public Integer getMaxItems() {
    return maxItems;
  }

  public Boolean getUniqueItems() {
    return uniqueItems;
  }

  public List<SchemaModel> getOneOf() {
    return oneOf;
  }

  public List<SchemaModel> getAnyOf() {
    return anyOf;
  }

  public List<SchemaModel> getAllOf() {
    return allOf;
  }

  public SchemaModel getNot() {
    return not;
  }

  public Object getAdditionalProperties() {
    return additionalProperties;
  }

  public Number getMultipleOf() {
    return multipleOf;
  }

  public Number getExclusiveMinimum() {
    return exclusiveMinimum;
  }

  public Number getExclusiveMaximum() {
    return exclusiveMaximum;
  }

  public Integer getMinProperties() {
    return minProperties;
  }

  public Integer getMaxProperties() {
    return maxProperties;
  }

  public Object getConstValue() {
    return constValue;
  }

  public String getRef() {
    return ref;
  }

  @Override
  protected Builder createBuilder() {
    return builder();
  }

  public static class Builder extends AbstractExtraPropsModel.Builder<SchemaModel, Builder> {
    public Builder() {
      model = new SchemaModel();
    }

    public Builder type(String type) {
      model.type = type;
      return this;
    }

    public Builder types(List<String> types) {
      model.types = types;
      return this;
    }

    public Builder format(String format) {
      model.format = format;
      return this;
    }

    public Builder title(String title) {
      model.title = title;
      return this;
    }

    public Builder description(String description) {
      model.description = description;
      return this;
    }

    public Builder defaultValue(Object defaultValue) {
      model.defaultValue = defaultValue;
      return this;
    }

    public Builder nullable(Boolean nullable) {
      model.nullable = nullable;
      return this;
    }

    public Builder readOnly(Boolean readOnly) {
      model.readOnly = readOnly;
      return this;
    }

    public Builder writeOnly(Boolean writeOnly) {
      model.writeOnly = writeOnly;
      return this;
    }

    public Builder example(Object example) {
      model.example = example;
      return this;
    }

    public Builder deprecated(Boolean deprecated) {
      model.deprecated = deprecated;
      return this;
    }

    public Builder enumValues(List<Object> enumValues) {
      model.enumValues = enumValues;
      return this;
    }

    public Builder properties(Map<String, SchemaModel> properties) {
      model.properties = properties;
      return this;
    }

    public Builder required(List<String> required) {
      model.required = required;
      return this;
    }

    public Builder items(SchemaModel items) {
      model.items = items;
      return this;
    }

    public Builder minimum(Number minimum) {
      model.minimum = minimum;
      return this;
    }

    public Builder maximum(Number maximum) {
      model.maximum = maximum;
      return this;
    }

    public Builder minLength(Integer minLength) {
      model.minLength = minLength;
      return this;
    }

    public Builder maxLength(Integer maxLength) {
      model.maxLength = maxLength;
      return this;
    }

    public Builder pattern(String pattern) {
      model.pattern = pattern;
      return this;
    }

    public Builder minItems(Integer minItems) {
      model.minItems = minItems;
      return this;
    }

    public Builder maxItems(Integer maxItems) {
      model.maxItems = maxItems;
      return this;
    }

    public Builder uniqueItems(Boolean uniqueItems) {
      model.uniqueItems = uniqueItems;
      return this;
    }

    public Builder oneOf(List<SchemaModel> oneOf) {
      model.oneOf = oneOf;
      return this;
    }

    public Builder anyOf(List<SchemaModel> anyOf) {
      model.anyOf = anyOf;
      return this;
    }

    public Builder allOf(List<SchemaModel> allOf) {
      model.allOf = allOf;
      return this;
    }

    public Builder not(SchemaModel not) {
      model.not = not;
      return this;
    }

    public Builder additionalProperties(Object additionalProperties) {
      model.additionalProperties = additionalProperties;
      return this;
    }

    public Builder multipleOf(Number multipleOf) {
      model.multipleOf = multipleOf;
      return this;
    }

    public Builder exclusiveMinimum(Number exclusiveMinimum) {
      model.exclusiveMinimum = exclusiveMinimum;
      return this;
    }

    public Builder exclusiveMaximum(Number exclusiveMaximum) {
      model.exclusiveMaximum = exclusiveMaximum;
      return this;
    }

    public Builder minProperties(Integer minProperties) {
      model.minProperties = minProperties;
      return this;
    }

    public Builder maxProperties(Integer maxProperties) {
      model.maxProperties = maxProperties;
      return this;
    }

    public Builder constValue(Object constValue) {
      model.constValue = constValue;
      return this;
    }

    public Builder ref(String ref) {
      model.ref = ref;
      return this;
    }

    /**
     * 便捷方法：字符串类型
     */
    public Builder stringType() {
      model.type = "string";
      return this;
    }

    /**
     * 便捷方法：数字类型
     */
    public Builder numberType() {
      model.type = "number";
      return this;
    }

    /**
     * 便捷方法：整数类型
     */
    public Builder integerType() {
      model.type = "integer";
      return this;
    }

    /**
     * 便捷方法：布尔类型
     */
    public Builder booleanType() {
      model.type = "boolean";
      return this;
    }

    /**
     * 便捷方法：数组类型
     */
    public Builder arrayType() {
      model.type = "array";
      return this;
    }

    /**
     * 便捷方法：对象类型
     */
    public Builder objectType() {
      model.type = "object";
      return this;
    }
  }
}

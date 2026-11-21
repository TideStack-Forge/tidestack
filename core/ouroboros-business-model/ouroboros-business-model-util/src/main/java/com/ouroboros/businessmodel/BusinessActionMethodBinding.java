package com.ouroboros.businessmodel;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.businessmodel.action.BusinessActionContext;
import com.ouroboros.businessmodel.action.BusinessActionInput;
import com.ouroboros.businessmodel.action.BusinessObjectReference;
import com.ouroboros.businessmodel.annotation.BusinessActionData;
import com.ouroboros.businessmodel.annotation.BusinessActionParam;
import com.ouroboros.businessmodel.behavior.ActionDefinition;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.Json;

/** Compiles the Java receiver shape once; the published Schema remains authoritative. */
final class BusinessActionMethodBinding {
  private static final ObjectMapper MAPPER = Json.mapperCopy()
      .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
      .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
  static {
    MAPPER.coercionConfigFor(com.fasterxml.jackson.databind.type.LogicalType.Textual)
        .setCoercion(com.fasterxml.jackson.databind.cfg.CoercionInputShape.Integer, com.fasterxml.jackson.databind.cfg.CoercionAction.Fail)
        .setCoercion(com.fasterxml.jackson.databind.cfg.CoercionInputShape.Float, com.fasterxml.jackson.databind.cfg.CoercionAction.Fail)
        .setCoercion(com.fasterxml.jackson.databind.cfg.CoercionInputShape.Boolean, com.fasterxml.jackson.databind.cfg.CoercionAction.Fail);
  }
  private final List<Argument> arguments;
  private final Method method;

  BusinessActionMethodBinding(Method method) {
    this.method = method;
    List<Argument> compiled = new ArrayList<>();
    Set<String> selected = new HashSet<>();
    for (var parameter : method.getParameters()) {
      var param = parameter.getAnnotation(BusinessActionParam.class);
      var data = parameter.getAnnotation(BusinessActionData.class);
      String partition;
      String field = "";
      if (param != null && data != null) {
        throw invalid("参数不能同时声明 Param 和 Data");
      }
      if (param != null || data != null) {
        partition = param != null ? "params" : "data";
        field = param != null ? param.value() : data.value();
        if (!field.isEmpty() && !field.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
          throw invalid("参数名必须是单个属性名");
        }
        requireConcreteType(parameter.getParameterizedType());
      } else if (parameter.getType() == BusinessActionContext.class) {
        partition = "context";
      } else if (parameter.getType() == BusinessActionInput.class) {
        partition = "input";
        if (!(parameter.getParameterizedType() instanceof ParameterizedType type)
            || java.util.Arrays.stream(type.getActualTypeArguments()).anyMatch(value -> !(value instanceof WildcardType wildcard)
                || wildcard.getLowerBounds().length != 0 || !java.util.Arrays.equals(wildcard.getUpperBounds(), new Type[]{Object.class}))) {
          throw invalid("整体输入须声明为 BusinessActionInput<?, ?>");
        }
      } else {
        throw invalid("业务参数必须声明 @BusinessActionParam 或 @BusinessActionData");
      }
      String key = partition + "/" + field;
      if (!selected.add(key)) {
        throw invalid("重复参数 " + key);
      }
      compiled.add(new Argument(partition, field, MAPPER.constructType(parameter.getParameterizedType()), parameter.getType().isPrimitive()));
    }
    if (selected.contains("input/") && compiled.stream().anyMatch(argument -> argument.isBusiness())) {
      throw invalid("整体 Input 不能与分区参数混用");
    }
    for (String partition : List.of("params", "data")) {
      if (selected.contains(partition + "/") && compiled.stream()
          .anyMatch(argument -> argument.partition().equals(partition) && !argument.field().isEmpty())) {
        throw invalid("整体分区不能与同分区属性参数混用");
      }
    }
    arguments = List.copyOf(compiled);
  }

  void validate(ActionDefinition action) {
    Map<?, ?> properties = object(action.getInputSchema().get("properties"));
    boolean entireInput = arguments.stream().anyMatch(argument -> "input".equals(argument.partition()));
    if (!entireInput) {
      for (String partition : List.of("params", "data")) {
        List<Argument> receivers = arguments.stream().filter(argument -> partition.equals(argument.partition())).toList();
        Map<?, ?> schema = object(properties.get(partition));
        if (properties.containsKey(partition) && receivers.isEmpty()) {
          throw invalid("契约分区没有接收参数 " + partition);
        }
        if (receivers.stream().anyMatch(argument -> argument.field().isEmpty())) {
          Argument receiver = receivers.get(0);
          if (!properties.containsKey(partition)) {
            throw invalid("契约中不存在分区 " + partition);
          }
          if (receiver.primitive() && ((!required(action.getInputSchema(), partition) && !schema.containsKey("default"))
              || nullable(schema))) {
            throw invalid("可缺失或可空分区不能绑定 primitive " + partition);
          }
          validateType(receiver.type(), schema);
          continue;
        }
        Map<?, ?> fields = object(schema.get("properties"));
        Set<String> names = new HashSet<>();
        for (Argument argument : receivers) {
          if (!fields.containsKey(argument.field())) {
            throw invalid("契约中不存在属性 " + partition + "/" + argument.field());
          }
          names.add(argument.field());
          Map<?, ?> field = object(fields.get(argument.field()));
          boolean requiredPartition = required(action.getInputSchema(), partition);
          if (argument.primitive() && ((!requiredPartition && !schema.containsKey("default"))
              || nullable(schema) || (!required(schema, argument.field()) && !field.containsKey("default")) || nullable(field))) {
            throw invalid("可缺失或可空属性不能绑定 primitive " + partition + "/" + argument.field());
          }
          validateType(argument.type(), field);
        }
        if (!names.containsAll(fields.keySet())) {
          throw invalid("契约属性缺少明确接收参数 " + partition);
        }
      }
    }
    validateType(MAPPER.constructType(method.getGenericReturnType()), action.getOutputSchema());
  }

  Object[] bind(BusinessActionContext context, BusinessActionInput<?, ?> input, Map<String, Object> inputSchema) {
    Object[] result = new Object[arguments.size()];
    Map<?, ?> properties = object(inputSchema.get("properties"));
    for (int index = 0; index < arguments.size(); index++) {
      Argument argument = arguments.get(index);
      if ("context".equals(argument.partition())) {
        result[index] = context;
      } else if ("input".equals(argument.partition())) {
        result[index] = input;
      } else {
        Object value = "params".equals(argument.partition()) ? input.params() : input.data();
        Map<?, ?> schema = object(properties.get(argument.partition()));
        if (!argument.field().isEmpty()) {
          value = value instanceof Map<?, ?> fields ? fields.get(argument.field()) : null;
          schema = object(object(schema.get("properties")).get(argument.field()));
        }
        result[index] = project(value, argument.type(), schema);
      }
    }
    return result;
  }

  private Object project(Object value, JavaType type, Map<?, ?> schema) {
    if (value == null) {
      if (type.isPrimitive()) {
        throw new BusinessException("业务 Action 参数缺失，不能绑定 primitive");
      }
      return null;
    }
    if (type.getRawClass() == BusinessObjectReference.class) {
      if (value instanceof Map<?, ?> reference && schema.get("x-business-object-ref") instanceof String model) {
        return new BusinessObjectReference(model, reference.get("id"));
      }
      throw new BusinessException("业务 Action 对象引用参数缺少模型契约");
    }
    if (!type.isContainerType() && type.getRawClass().isInstance(value)) {
      return value;
    }
    if ((type.isCollectionLikeType() || type.isArrayType()) && value instanceof List<?> values) {
      List<Object> projected = new ArrayList<>();
      for (Object item : values) {
        projected.add(project(item, type.getContentType(), object(schema.get("items"))));
      }
      return type.getRawClass().isAssignableFrom(ArrayList.class) ? projected : MAPPER.convertValue(projected, type);
    }
    try {
      if (value instanceof Map<?, ?> values && !type.isMapLikeType() && type.getRawClass() != Object.class) {
        Map<String, Object> projected = new java.util.LinkedHashMap<>();
        var properties = MAPPER.getDeserializationConfig().introspect(type).findProperties();
        Map<?, ?> fields = object(schema.get("properties"));
        values.forEach((key, nested) -> {
          String name = String.valueOf(key);
          var property = properties.stream().filter(candidate -> candidate.getName().equals(name)).findFirst();
          projected.put(name, property.isPresent()
              ? project(nested, property.get().getPrimaryType(), object(fields.get(key))) : nested);
        });
        return MAPPER.convertValue(projected, type);
      }
      return MAPPER.convertValue(value, type);
    } catch (IllegalArgumentException error) {
      throw new BusinessException("业务 Action 参数无法绑定至声明的 Java 类型", error);
    }
  }

  private void validateType(JavaType type, Map<?, ?> schema) {
    Object kind = schema.get("type");
    if (kind instanceof List<?> types) {
      kind = types.stream().filter(value -> !"null".equals(value)).findFirst().orElse(null);
    }
    Class<?> raw = type.getRawClass();
    boolean compatible = kind == null || raw == Object.class || switch (String.valueOf(kind)) {
      case "boolean" -> raw == boolean.class || raw == Boolean.class;
      case "integer" -> Number.class.isAssignableFrom(type.isPrimitive() ? boxed(raw) : raw);
      case "number" -> raw == double.class || raw == Double.class || raw == float.class || raw == Float.class
          || raw == java.math.BigDecimal.class || raw == Number.class;
      case "string" -> raw == String.class || raw.isEnum();
      case "array" -> type.isCollectionLikeType() || type.isArrayType();
      case "null" -> !raw.isPrimitive() || raw == void.class;
      case "object" -> !raw.isPrimitive() && raw != String.class && !Number.class.isAssignableFrom(raw)
          && raw != Boolean.class && !type.isCollectionLikeType();
      default -> false;
    };
    if (!compatible) {
      throw invalid("Java 类型与 Schema 不兼容 " + type);
    }
    if ((type.isCollectionLikeType() || type.isArrayType()) && schema.get("items") instanceof Map<?, ?> items) {
      validateType(type.getContentType(), items);
    }
    if (raw == BusinessObjectReference.class && !(schema.get("x-business-object-ref") instanceof String)) {
      throw invalid("BusinessObjectReference 必须声明对象模型");
    }
    if ("object".equals(kind) && raw != Object.class && raw != BusinessObjectReference.class && !type.isMapLikeType()
        && schema.get("properties") instanceof Map<?, ?> fields) {
      var receivers = MAPPER.getDeserializationConfig().introspect(type).findProperties();
      if (receivers.stream().anyMatch(property -> property.getPrimaryType().isPrimitive()
          && !fields.containsKey(property.getName()))) {
        throw invalid("DTO primitive 属性必须在契约中声明");
      }
      for (var field : fields.entrySet()) {
        var property = receivers.stream().filter(candidate -> candidate.getName().equals(field.getKey())).findFirst()
            .orElseThrow(() -> invalid("DTO 缺少契约属性 " + field.getKey()));
        var fieldSchema = object(field.getValue());
        if (property.getPrimaryType().isPrimitive() && ((!required(schema, String.valueOf(field.getKey()))
            && !fieldSchema.containsKey("default")) || nullable(fieldSchema))) {
          throw invalid("DTO 可选或可空属性不能绑定 primitive " + field.getKey());
        }
        validateType(property.getPrimaryType(), fieldSchema);
      }
    }
  }

  private static Class<?> boxed(Class<?> type) {
    if (type == int.class) { return Integer.class; }
    if (type == long.class) { return Long.class; }
    if (type == double.class) { return Double.class; }
    if (type == float.class) { return Float.class; }
    if (type == short.class) { return Short.class; }
    if (type == byte.class) { return Byte.class; }
    return type;
  }

  private void requireConcreteType(Type type) {
    if (type instanceof Class<?> raw && raw.getTypeParameters().length > 0) {
      throw invalid("业务参数不能使用 raw 泛型");
    }
    if (type instanceof ParameterizedType parameterized) {
      for (Type nested : parameterized.getActualTypeArguments()) {
        requireConcreteType(nested);
      }
    } else if (!(type instanceof Class<?>)) {
      throw invalid("业务参数泛型必须可解析");
    }
  }

  private static boolean required(Map<?, ?> schema, String field) {
    return schema.get("required") instanceof List<?> required && required.contains(field);
  }

  private static boolean nullable(Map<?, ?> schema) {
    return !schema.containsKey("type") || "null".equals(schema.get("type"))
        || (schema.get("type") instanceof List<?> types && types.contains("null"));
  }

  private static Map<?, ?> object(Object value) {
    return value instanceof Map<?, ?> map ? map : Map.of();
  }

  private IllegalStateException invalid(String reason) {
    return new IllegalStateException(method.getDeclaringClass().getName() + "#" + method.getName() + ": " + reason);
  }

  private record Argument(String partition, String field, JavaType type, boolean primitive) {
    boolean isBusiness() {
      return "params".equals(partition) || "data".equals(partition);
    }
  }
}

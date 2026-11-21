package com.ouroboros.logicflow.nodes;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.ouroboros.logicflow.exception.NodeDesignException;
import com.ouroboros.logicflow.model.*;

public class ConstNode extends AbstractNode<Object> {
  final private List<Object> outputValue;
  final private List<FlowNodePort> outputPorts;

  protected ConstNode(FlowModel flowModel, FlowNodeMeta meta) {
    super(flowModel, meta);
    var dataType = meta.getExtraProp(String.class, "dataType").orElse("any");
    var dataTypeEnum = DataTypeEnum.fromValue(dataType);
    outputPorts = Collections.singletonList(new DefaultFlowNodeOutPort(this, dataTypeEnum));
    var value = meta.getExtraProp("value").orElse(null);
    outputValue = Collections.singletonList(convertValue(dataTypeEnum, value, meta));
  }

  private Object convertValue(DataTypeEnum dataType, Object value, FlowNodeMeta meta) {
    return switch (dataType) {
      case STRING -> requireType(value instanceof String ? value : null, meta);
      case INTEGER -> toInteger(value, meta);
      case LONG -> toLong(value, meta);
      case FLOAT -> toFloat(value, meta);
      case DOUBLE -> toDouble(value, meta);
      case BOOLEAN -> toBoolean(value, meta);
      case DATE -> toLocalDate(value, meta);
      case DATETIME -> toLocalDateTime(value, meta);
      case TIME -> toLocalTime(value, meta);
      case ARRAY -> requireType(value instanceof Collection<?> ? value : null, meta);
      case MAP, OBJECT -> requireType(value instanceof Map<?, ?> ? value : null, meta);
      case ANY -> value;
    };
  }

  private Object requireType(Object value, FlowNodeMeta meta) {
    if (value == null) {
      return invalidValue(meta);
    }
    return value;
  }

  private Object invalidValue(FlowNodeMeta meta) {
    throw new NodeDesignException("常量值不符合所选值类型", meta, "value");
  }

  private Integer toInteger(Object value, FlowNodeMeta meta) {
    if (value instanceof Integer integerValue) {
      return integerValue;
    }
    var longValue = toLong(value, meta);
    if (longValue < Integer.MIN_VALUE || longValue > Integer.MAX_VALUE) {
      return (Integer) invalidValue(meta);
    }
    return longValue.intValue();
  }

  private Long toLong(Object value, FlowNodeMeta meta) {
    if (value instanceof Long longValue) {
      return longValue;
    }
    if (value instanceof Integer integerValue) {
      return integerValue.longValue();
    }
    if (value instanceof Number numberValue) {
      var doubleValue = numberValue.doubleValue();
      if (!Double.isFinite(doubleValue) || doubleValue % 1 != 0 || doubleValue < Long.MIN_VALUE || doubleValue > Long.MAX_VALUE) {
        return (Long) invalidValue(meta);
      }
      return numberValue.longValue();
    }
    if (value instanceof CharSequence textValue) {
      var text = textValue.toString().trim();
      if (!text.matches("^-?\\d+$")) {
        return (Long) invalidValue(meta);
      }
      try {
        return Long.parseLong(text);
      } catch (NumberFormatException e) {
        return (Long) invalidValue(meta);
      }
    }
    return (Long) invalidValue(meta);
  }

  private Float toFloat(Object value, FlowNodeMeta meta) {
    if (value instanceof Float floatValue) {
      return floatValue;
    }
    if (value instanceof Number numberValue) {
      var floatValue = numberValue.floatValue();
      return Float.isFinite(floatValue) ? floatValue : (Float) invalidValue(meta);
    }
    if (value instanceof CharSequence textValue) {
      try {
        var floatValue = Float.parseFloat(textValue.toString().trim());
        return Float.isFinite(floatValue) ? floatValue : (Float) invalidValue(meta);
      } catch (NumberFormatException e) {
        return (Float) invalidValue(meta);
      }
    }
    return (Float) invalidValue(meta);
  }

  private Double toDouble(Object value, FlowNodeMeta meta) {
    if (value instanceof Double doubleValue) {
      return doubleValue;
    }
    if (value instanceof Number numberValue) {
      var doubleValue = numberValue.doubleValue();
      return Double.isFinite(doubleValue) ? doubleValue : (Double) invalidValue(meta);
    }
    if (value instanceof CharSequence textValue) {
      try {
        var doubleValue = Double.parseDouble(textValue.toString().trim());
        return Double.isFinite(doubleValue) ? doubleValue : (Double) invalidValue(meta);
      } catch (NumberFormatException e) {
        return (Double) invalidValue(meta);
      }
    }
    return (Double) invalidValue(meta);
  }

  private Boolean toBoolean(Object value, FlowNodeMeta meta) {
    if (value instanceof Boolean booleanValue) {
      return booleanValue;
    }
    if (value instanceof CharSequence textValue) {
      var text = textValue.toString().trim();
      if ("true".equals(text)) {
        return true;
      }
      if ("false".equals(text)) {
        return false;
      }
    }
    return (Boolean) invalidValue(meta);
  }

  private LocalDate toLocalDate(Object value, FlowNodeMeta meta) {
    if (value instanceof LocalDate localDate) {
      return localDate;
    }
    if (!(value instanceof CharSequence textValue)) {
      return (LocalDate) invalidValue(meta);
    }
    try {
      return LocalDate.parse(textValue.toString().trim());
    } catch (DateTimeException e) {
      return (LocalDate) invalidValue(meta);
    }
  }

  private LocalDateTime toLocalDateTime(Object value, FlowNodeMeta meta) {
    if (value instanceof LocalDateTime localDateTime) {
      return localDateTime;
    }
    if (!(value instanceof CharSequence textValue)) {
      return (LocalDateTime) invalidValue(meta);
    }
    var text = textValue.toString().trim();
    try {
      return LocalDateTime.parse(text);
    } catch (DateTimeException e) {
      try {
        return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
      } catch (DateTimeException fallbackParseException) {
        try {
          return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        } catch (DateTimeException finalParseException) {
          return (LocalDateTime) invalidValue(meta);
        }
      }
    }
  }

  private LocalTime toLocalTime(Object value, FlowNodeMeta meta) {
    if (value instanceof LocalTime localTime) {
      return localTime;
    }
    if (!(value instanceof CharSequence textValue)) {
      return (LocalTime) invalidValue(meta);
    }
    try {
      return LocalTime.parse(textValue.toString().trim());
    } catch (DateTimeException e) {
      return (LocalTime) invalidValue(meta);
    }
  }

  @Override
  protected List<Object> doExecute(List<Object> inputs) {
    return outputValue;
  }

  @Override
  public List<FlowNodePort> getOutputPorts() {
    return outputPorts;
  }

  @Override
  protected Integer minInputPortCount() {
    return 0;
  }

  public static class Builder implements NodeBuilder<ConstNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return "const".equalsIgnoreCase(meta.getType());
    }

    @Override
    public ConstNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new ConstNode(owner, metadata);
    }
  }
}

package com.ouroboros.businessmodel.action;

import java.util.Map;
import java.util.function.Function;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.JsonSchemas;

/** Immutable validation plan owned by one published behavior snapshot. */
public final class BusinessActionInputPlan {
  private final Map<String, Object> schema;
  private final Function<Object, Boolean> validator;
  private final com.ouroboros.businessmodel.behavior.BusinessBehaviorMappingPlan flowMapping;
  private final boolean hasFlowMapping;

  public BusinessActionInputPlan(Map<String, Object> schema) {
    this(schema, Map.of());
  }

  public BusinessActionInputPlan(Map<String, Object> schema, Map<String, Object> mapping) {
    this(schema, mapping, new com.ouroboros.businessmodel.behavior.BehaviorDefinitionSource("programmatic", "binding", null));
  }

  public BusinessActionInputPlan(Map<String, Object> schema, Map<String, Object> mapping,
                                com.ouroboros.businessmodel.behavior.BehaviorDefinitionSource source) {
    this.schema = BusinessActionInputValues.snapshotMap(schema);
    this.validator = JsonSchemas.buildValidator(this.schema);
    this.flowMapping = com.ouroboros.businessmodel.behavior.BusinessBehaviorMappingPlan.flowInput(mapping, schema, source);
    this.hasFlowMapping = !mapping.isEmpty();
  }

  public PreparedBusinessActionInput prepare(BusinessActionInput<?, ?> input) {
    return PreparedBusinessActionInput.prepare(input, this);
  }

  /** A thin view with invocation-local mutable work values; the validated Input stays unchanged. */
  public Map<String, Object> flowInput(PreparedBusinessActionInput input, Map<String, Object> context) {
    return new BusinessActionFlowInput(flowValues(input, context), context.get("request"));
  }

  private Map<String, Object> flowValues(PreparedBusinessActionInput input, Map<String, Object> context) {
    if (!hasFlowMapping) {
      return new java.util.AbstractMap<>() {
        private final Map<String, Object> work = new java.util.LinkedHashMap<>();
        @Override
        public Object get(Object key) {
          if ("context".equals(key)) { return context; }
          if (!(key instanceof String name) || !input.containsKey(name)) { return null; }
          if (!work.containsKey(name)) { work.put(name, BusinessActionInputValues.workCopy(input.get(name))); }
          return work.get(name);
        }
        @Override
        public boolean containsKey(Object key) { return "context".equals(key) || input.containsKey(key); }
        @Override
        public java.util.Set<String> keySet() {
          var keys = new java.util.LinkedHashSet<>(input.keySet());
          keys.add("context");
          return java.util.Collections.unmodifiableSet(keys);
        }
        @Override
        public java.util.Set<Entry<String, Object>> entrySet() {
          return PreparedBusinessActionInput.entries(this);
        }
      };
    }
    Map<String, Object> source = Map.of("input", input, "context", context);
    return flowMapping.map(source, value -> value == context || value == context.get("request")
        ? value : BusinessActionInputValues.workCopy(value));
  }

  Map<String, Object> schema() {
    return schema;
  }

  void validate(Object value) {
    Object validationValue = BusinessActionInputValues.validationValue(schema, value);
    if (!validator.apply(validationValue)) {
      String locations = JsonSchemas.validateResult(schema, validationValue)
          .getMessages().stream().map(message -> message.getKeyword() + " @ " + message.getInstanceLocation())
          .collect(java.util.stream.Collectors.joining("; "));
      throw new BusinessException("业务 Action 输入不符合契约：" + locations);
    }
  }
}

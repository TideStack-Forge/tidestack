package com.ouroboros.logicflow.nodes;

import java.util.Collections;
import java.util.Map;

import com.ouroboros.logicflow.exception.ValidationFailedException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.util.JsonSchemas;

public class ValidateNode extends AbstractSimpleNode<Object> {
  Map<String, Object> schema;

  public ValidateNode(FlowModel flowModel, FlowNodeMeta meta) {
    super(flowModel, meta);
    schema = meta.getExtraProp(Map.class, "schema").orElse(Collections.emptyMap());
  }

  @Override
  protected Object doExecute(Object input) {
    if (schema.isEmpty()) {
      return input;
    }
    var validationResult = JsonSchemas.validateResult(schema, input);
    if (validationResult.isValid()) {
      return input;
    }
    throw new ValidationFailedException(
        "输入不符合预期的格式: " + validationResult.toMessageString(),
        this,
        Collections.singletonList(input));
  }

  public static class Builder implements NodeBuilder<ValidateNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return "validate".equalsIgnoreCase(meta.getType());
    }

    @Override
    public ValidateNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new ValidateNode(owner, metadata);
    }
  }
}

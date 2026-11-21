package com.ouroboros.logicflow.nodes;

import java.util.List;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;

public class OuroborosExceptionsNode extends AbstractTerminalNode {
  final private String type;
  final private Integer code;
  final private String message;

  protected OuroborosExceptionsNode(FlowModel flowModel, FlowNodeMeta meta) {
    super(flowModel, meta);
    type = meta.getExtraProp(String.class, "exceptionType").orElse("BusinessException");
    code = meta.getExtraProp(Integer.class, "exceptionCode").orElse(-1);
    message = meta.getExtraProp(String.class, "exceptionMessage").orElse("未知错误");
  }

  @Override
  protected List<Object> doExecute(List<Object> inputs) {
    if ("BusinessException".equalsIgnoreCase(type)) {
      throw new BusinessException(code, message);
    } else if ("PlatformException".equalsIgnoreCase(type)) {
      throw new PlatformException(code, message);
    } else {
      throw new InternalException(code, message);
    }
  }

  @Override
  protected Integer maxOutputPortCount() {
    return 0;
  }

  @Override
  protected Integer minOutputPortCount() {
    return 0;
  }

  @Override
  protected Integer minInputPortCount() {
    return 1;
  }

  public static class Builder implements NodeBuilder<OuroborosExceptionsNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return "ouroborosExceptions".equalsIgnoreCase(meta.getType());
    }

    @Override
    public OuroborosExceptionsNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new OuroborosExceptionsNode(owner, metadata);
    }
  }
}

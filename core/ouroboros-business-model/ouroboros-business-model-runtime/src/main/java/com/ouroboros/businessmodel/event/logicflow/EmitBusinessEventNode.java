package com.ouroboros.businessmodel.event.logicflow;

import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

import com.ouroboros.businessmodel.event.BusinessEventEmissionContext;
import com.ouroboros.businessmodel.event.BusinessEventIdentity;
import com.ouroboros.businessmodel.event.BusinessEventPort;
import com.ouroboros.businessmodel.event.BusinessEventSubject;
import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.exception.NodeDesignException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.nodes.AbstractSimpleNode;
import com.ouroboros.logicflow.nodes.NodeBuilder;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;

/** Low-code Event producer using the same governed port as high-code producers. */
public class EmitBusinessEventNode extends AbstractSimpleNode<Object> {
  private final String modelFullName;
  private final String eventName;
  private final String producerRef;
  private final Supplier<BusinessEventPort> port;

  public EmitBusinessEventNode(FlowModel owner, FlowNodeMeta meta, Supplier<BusinessEventPort> port) {
    super(owner, meta);
    this.port = port;
    modelFullName = meta.getExtraProp(String.class, "modelFullName").orElse("");
    eventName = meta.getExtraProp(String.class, "eventName").orElse("");
    producerRef = meta.getExtraProp(String.class, "producerRef").filter(value -> !value.isBlank()).orElse(null);
    if (modelFullName.isBlank() || eventName.isBlank()) {
      addError(new NodeDesignException("业务 Event 节点需要所属模型与事件名", meta));
    }
  }

  @Override
  protected Object doExecute(Object input) {
    if (!(input instanceof Map<?, ?> values) || !values.containsKey("payload")) {
      throw new FlowRuntimeException("业务 Event 输入需要 payload 和可选 subjectId", this,
          Collections.singletonList(input));
    }
    var subject = new BusinessEventSubject(modelFullName, values.get("subjectId"));
    var context = new BusinessEventEmissionContext(producerRef,
        text(values.get("correlationId")), text(values.get("causationId")));
    return port.get().emit(new BusinessEventIdentity(modelFullName, eventName), subject,
        values.get("payload"), context).toMap();
  }

  private String text(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  public static class Builder implements NodeBuilder<EmitBusinessEventNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return "emitBusinessEvent".equalsIgnoreCase(meta.getType());
    }

    @Override
    public EmitBusinessEventNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new EmitBusinessEventNode(owner, metadata, () -> SpringContextUtil.getBean(BusinessEventPort.class));
    }
  }
}

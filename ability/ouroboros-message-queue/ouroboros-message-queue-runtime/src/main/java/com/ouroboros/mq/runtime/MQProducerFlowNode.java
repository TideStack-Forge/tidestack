package com.ouroboros.mq.runtime;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.exception.NodeDesignException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.nodes.AbstractNode;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;
import com.ouroboros.logicflow.nodes.NodeBuilder;
import com.ouroboros.mq.MQBridge;

@Component
public class MQProducerFlowNode {
  private static MQBridge mqBridge;

  public MQProducerFlowNode(MQBridge mqBridge) {
    MQProducerFlowNode.mqBridge = mqBridge;
  }

  public static class MQProducerFlowNodeImpl extends AbstractNode<Object> {
    String bindingName;

    protected MQProducerFlowNodeImpl(FlowModel flowModel, FlowNodeMeta meta) {
      super(flowModel, meta);
      this.bindingName = meta.getExtraProp(String.class, "bindingName").orElse("");
      if (StringUtils.isBlank(bindingName)) {
        addError(new NodeDesignException("消息队列生产者节点未配置 bindingName", meta));
      }
    }

    @Override
    protected Integer maxInputPortCount() {
      return 2;
    }

    @Override
    protected List<Object> doExecute(List<Object> inputs) {
      if (inputs == null || inputs.isEmpty()) {
        throw new FlowRuntimeException("消息队列生产者节点的第一个输入不得缺失", this, inputs);
      }
      var payload = inputs.get(0);
      var headers = inputs.size() > 1 ? inputs.get(1) : Collections.emptyMap();
      if (headers == null) {
        headers = Collections.emptyMap();
      }
      if (payload == null) {
        throw new FlowRuntimeException("消息队列生产者节点的第一个输入不得为 null", this, inputs);
      }
      if (!(headers instanceof Map<?, ?> headerMap)) {
        throw new FlowRuntimeException("消息队列生产者节点的第二个输入必须为 Map 类型", this, inputs);
      }
      var message = MessageBuilder.withPayload(payload)
          .copyHeadersIfAbsent((Map<String, ?>) headerMap)
          .build();
      mqBridge.send(bindingName, message);
      return Collections.singletonList(payload);
    }
  }

  public static class Builder implements NodeBuilder<MQProducerFlowNodeImpl> {

    @Override
    public Boolean support(FlowNodeMeta meta) {
      return "mqProducer".equalsIgnoreCase(meta.getType());
    }

    @Override
    public MQProducerFlowNodeImpl build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new MQProducerFlowNodeImpl(owner, metadata);
    }
  }
}

package com.ouroboros.notification.logicflow.nodes;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.exception.NodeDesignException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.nodes.AbstractNode;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;
import com.ouroboros.logicflow.nodes.NodeBuilder;
import com.ouroboros.notification.AddressNotificationDispatcher;
import com.ouroboros.notification.DispatchOptions;
import com.ouroboros.notification.EmployeeNotificationDispatcher;
import com.ouroboros.util.Json;

/**
 * @author liansz
 **/
@Component
public class SendNotificationNode {

  private static AddressNotificationDispatcher addressNotificationDispatcher;
  private static EmployeeNotificationDispatcher employeeNotificationDispatcher;

  public SendNotificationNode(AddressNotificationDispatcher addressNotificationDispatcher,
                              EmployeeNotificationDispatcher employeeNotificationDispatcher) {
    SendNotificationNode.addressNotificationDispatcher = addressNotificationDispatcher;
    SendNotificationNode.employeeNotificationDispatcher = employeeNotificationDispatcher;
  }

  public static class Node extends AbstractNode<Object> {
    private final String template;
    private final String sendMode;

    protected Node(FlowModel flowModel, FlowNodeMeta meta) {
      super(flowModel, meta);
      template = meta.getExtraProp(String.class, "template")
          .map(String::trim)
          .orElse("");
      if (template.isEmpty()) {
        throw new NodeDesignException("未配置通知模版", meta);
      }

      sendMode = meta.getExtraProp(String.class, "sendMode")
          .map(String::trim)
          .orElse("employee");
      if (!"employee".equals(sendMode) && !"address".equals(sendMode)) {
        throw new NodeDesignException("通知发送模式不正确", meta);
      }
    }

    @Override
    protected List<Object> doExecute(List<Object> inputs) {
      if (inputs.size() != 1) {
        throw new FlowRuntimeException("通知节点只能接受一个输入", this, inputs);
      }

      final var input = (Map<String, Object>) inputs.get(0);
      if (!(input instanceof Map<?, ?>)) {
        throw new FlowRuntimeException("通知节点输入数据类型不匹配", this, inputs);
      }

      Map<String, Object> payload = (Map<String, Object>) input.get("payload");
      DispatchOptions options = Json.toBean(
          DispatchOptions.class,
          Json.toJsonString(input.getOrDefault("options", Collections.emptyMap()))
      );

      switch (sendMode) {
        case "address" -> {
          Map<String, Collection<String>> target = (Map<String, Collection<String>>) input.get("target");
          addressNotificationDispatcher.send(target, template, payload, options);
        }

        case "employee" -> {
          List<String> target = input.get("target") instanceof List
              ? (List<String>) input.get("target")
              : Collections.singletonList((String) input.get("target"));

          employeeNotificationDispatcher.send(target, template, payload, options);
        }
      }

      return inputs;
    }

    @Override
    protected Integer maxInputPortCount() {
      return 1;
    }

    @Override
    protected Integer minOutputPortCount() {
      return 0;
    }
  }

  public static class Builder implements NodeBuilder<SendNotificationNode.Node> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return "sendNotification".equalsIgnoreCase(meta.getType());
    }

    @Override
    public SendNotificationNode.Node build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new SendNotificationNode.Node(owner, metadata);
    }
  }
}

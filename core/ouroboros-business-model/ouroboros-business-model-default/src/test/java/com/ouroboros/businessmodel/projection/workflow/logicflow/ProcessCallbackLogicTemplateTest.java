package com.ouroboros.businessmodel.projection.workflow.logicflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import com.ouroboros.businessmodel.util.PrototypeTemplate;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.logicflow.model.FlowModelFactory;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.logicflow.nodes.DataModelReferences;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;
import com.ouroboros.logicflow.nodes.NodeReferenceSet;
import io.vavr.control.Try;

public class ProcessCallbackLogicTemplateTest {
  @Test
  public void processCallbackTemplateShouldUseCoreSupportedSwitchRouting() throws Exception {
    var dataModel = stubDataModel("demo.order");

    var params = new LinkedHashMap<String, Object>();
    params.put("fullName", "demo.order.processCallback");
    params.put("modelTitle", "订单");
    params.put("modelName", "demo.order");
    params.put("processId", "process-demo");
    params.put("statusField", "status");
    var statusMap = createStatusMap();
    params.put("statusMap", statusMap);
    params.put("statusMapJson", com.ouroboros.util.Json.toJsonString(statusMap));

    var flowMeta = PrototypeTemplate.generate("logic-flow/workflow/processCallback", params, FlowModelMeta.class);
    var routerNode = flowMeta.getNodes().stream()
        .filter(node -> "流程状态路由".equals(node.getName()))
        .findFirst()
        .orElse(null);

    assertNotNull(routerNode);
    assertEquals("switch", routerNode.getType());
    assertFalse(flowMeta.getNodes().stream().anyMatch(node -> "processStatusRouter".equals(node.getType())));
    assertEquals(8, routerNode.getOutputPorts().size());
    assertEquals(7, ((List<?>) routerNode.getExtraProp("conditions").orElse(Collections.emptyList())).size());

    var statusScript = flowMeta.getNodes().stream()
        .filter(node -> "提取流程状态代码".equals(node.getName()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing status extraction node"))
        .getExtraProp("script")
        .map(String::valueOf)
        .orElse("");
    assertFalse("统一载荷下不应再解包伪 HTTP request", statusScript.contains("request.get('data')"));
    assertTrue(statusScript.contains("payload.get('outcome')"));
    assertTrue(statusScript.contains("payload.get('eventType')"));
    assertTrue(statusScript.contains("eventType == 'STARTED'"));
    assertTrue(statusScript.contains("return 'PROCESSING'"));
    assertTrue(statusScript.contains("eventType == 'SUSPENDED'"));
    assertTrue(statusScript.contains("return 'SUSPENDED'"));
    assertTrue(statusScript.contains("eventType == 'WITHDRAWN'"));
    assertTrue(statusScript.contains("return 'WITHDRAWN'"));
    assertTrue(statusScript.contains("outcome == 'approved' || eventType == 'APPROVED'"));
    assertTrue(statusScript.contains("return 'APPROVED'"));
    assertTrue(statusScript.contains("outcome == 'rejected' || eventType == 'REJECTED'"));
    assertTrue(statusScript.contains("return 'VETO'"));
    assertTrue(statusScript.contains("eventType == 'CANCELLED'"));
    assertTrue(statusScript.contains("return 'CANCELED'"));
    assertTrue(statusScript.contains("eventType == 'FAILED'"));
    assertFalse(statusScript.contains("finance-approved"));

    var mappingNode = flowMeta.getNodes().stream()
        .filter(node -> "准备状态更新参数".equals(node.getName()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing status update mapping node"));
    assertEquals("mapping", mappingNode.getType());
    var mapping = (Map<?, ?>) mappingNode.getExtraProp("mapping").orElse(Collections.emptyMap());
    assertEquals("$businessContext.recordId", mapping.get("where"));
    assertEquals("$statusCode", mapping.get("data.status"));

    var updateNode = flowMeta.getNodes().stream()
        .filter(node -> "更新业务状态".equals(node.getName()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing business status update node"));
    assertEquals("partialUpdateData", updateNode.getType());
    assertEquals("demo.order", updateNode.getExtraProp("dataModel").orElse(null));

    var references = NodeReferenceCatalog.of(List.of(NodeReferenceSet.complete(
        DataModelReferences.KIND,
        Map.of("demo.order", dataModel),
        String::compareToIgnoreCase
    )));
    var flowModel = FlowModelFactory.build(flowMeta, references);
    assertTrue("Expected generated process callback flow to be valid, but found: " + flowModel.getErrors(), flowModel.isValid());
  }

  private static DataModel stubDataModel(String fullName) {
    var lastDotIndex = fullName.lastIndexOf('.');
    var namespace = lastDotIndex >= 0 ? fullName.substring(0, lastDotIndex) : "";
    var name = lastDotIndex >= 0 ? fullName.substring(lastDotIndex + 1) : fullName;
    var self = new AtomicReference<DataModel>();
    InvocationHandler handler = (proxy, method, args) -> {
      switch (method.getName()) {
        case "getFullName":
          return fullName;
        case "getNamespace":
          return namespace;
        case "getName":
        case "getLabel":
        case "getRawName":
          return name;
        case "getVersion":
        case "getSource":
        case "getDescription":
          return "";
        case "getExtraProps":
          return Collections.emptyMap();
        case "getExtraProp":
          return Optional.empty();
        case "getFields":
        case "getPrimaryKeys":
          return Collections.emptyList();
        case "getField":
          return Optional.empty();
        case "withPlugins":
        case "withoutPlugins":
          return self.get();
        case "hasPlugin":
          return false;
        case "toString":
          return "StubDataModel(" + fullName + ")";
        case "hashCode":
          return System.identityHashCode(proxy);
        case "equals":
          return proxy == args[0];
        default:
          Class<?> returnType = method.getReturnType();
          if (Optional.class.equals(returnType)) {
            return Optional.empty();
          }
          if (List.class.equals(returnType)) {
            return Collections.emptyList();
          }
          if (Map.class.equals(returnType)) {
            return Collections.emptyMap();
          }
          if (Boolean.TYPE.equals(returnType)) {
            return false;
          }
          if (Integer.TYPE.equals(returnType)) {
            return 0;
          }
          if (Long.TYPE.equals(returnType)) {
            return 0L;
          }
          if (Try.class.equals(returnType)) {
            return Try.failure(new UnsupportedOperationException("Stub DataModel should not execute in contract tests"));
          }
          return null;
      }
    };
    var proxy = (DataModel) Proxy.newProxyInstance(
        DataModel.class.getClassLoader(),
        new Class<?>[]{DataModel.class},
        handler);
    self.set(proxy);
    return proxy;
  }

  private static Map<String, Object> createStatusMap() {
    var statusMap = new LinkedHashMap<String, Object>();
    statusMap.put("processing", status("PROCESSING", "审批中"));
    statusMap.put("suspended", status("SUSPENDED", "已暂停"));
    statusMap.put("withdrawn", status("WITHDRAWN", "已撤回"));
    statusMap.put("approved", status("APPROVED", "通过"));
    statusMap.put("veto", status("VETO", "否决"));
    statusMap.put("canceled", status("CANCELED", "撤销"));
    statusMap.put("terminated", status("TERMINATED", "终止"));
    return statusMap;
  }

  private static Map<String, Object> status(String code, String label) {
    var status = new LinkedHashMap<String, Object>();
    status.put("code", code);
    status.put("label", label);
    return status;
  }
}

package com.ouroboros.logicflow.nodes;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Test;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelFieldMeta;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.MetadataOnlyDataModel;
import com.ouroboros.logicflow.exception.DesignException;
import com.ouroboros.logicflow.exception.NodeDesignException;
import com.ouroboros.logicflow.exception.ProblematicNodesDetectedException;
import com.ouroboros.logicflow.model.DefaultFlowModel;
import com.ouroboros.logicflow.model.FlowModelFactory;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.model.FlowNodePortMeta;

public class DataModelNodeDesignValidationTest {

  @Test
  public void dataModelNodeUsesExplicitReferenceCatalog() {
    var designModel = model("demo.Customer");
    var references = NodeReferenceCatalog.of(List.of(
        NodeReferenceSet.complete(
            DataModelReferences.KIND,
            Map.of(designModel.getFullName(), designModel),
            String::compareToIgnoreCase
        )
    ));
    var flowModel = FlowModelFactory.build(
        flow("demo.Customer"),
        references
    );

    var messages = errors(flowModel).stream()
        .map(Throwable::getMessage)
        .collect(Collectors.joining("\n"));
    assertFalse(messages, messages.contains("未找到数据模型"));
    assertTrue(flowModel.getErrors().toString(), flowModel.isValid());
    assertTrue("A configured model reference does not require an input", flowModel.getNode("模型").getInputPorts().isEmpty());
    assertSame(designModel, flowModel.execute().get());
  }

  @Test
  public void dataModelNodeMissingModelMessageDoesNotRenderOptionalWrapper() {
    var references = NodeReferenceCatalog.of(List.of(
        NodeReferenceSet.complete(DataModelReferences.KIND, Collections.emptyMap())
    ));
    var flowModel = FlowModelFactory.build(flow("MissingModel"), references);

    assertFalse(flowModel.isValid());
    var messages = errors(flowModel).stream()
        .map(Throwable::getMessage)
        .collect(Collectors.joining("\n"));
    assertTrue(messages, messages.contains("未找到数据模型：MissingModel"));
    assertFalse(messages, messages.contains("Optional"));
  }

  @Test
  public void incompleteCatalogDoesNotReportAMissingDataModel() {
    var references = NodeReferenceCatalog.of(List.of(NodeReferenceSet.indeterminate(
        DataModelReferences.KIND,
        Collections.emptyMap(),
        NodeReferenceIndeterminateReason.NOT_READY
    )));
    var flowModel = FlowModelFactory.build(flow("UnknownModel"), references);

    var messages = errors(flowModel).stream()
        .map(Throwable::getMessage)
        .collect(Collectors.joining("\n"));
    assertTrue(messages, messages.contains("数据模型引用目录不可用（NOT_READY）"));
    assertFalse(messages, messages.contains("未找到数据模型"));
  }

  private static FlowModelMeta flow(String dataModelName) {
    var node = FlowNodeMeta.builder()
        .name("模型")
        .type("dataModel")
        .outputPort(FlowNodePortMeta.builder().name("model").build())
        .extraProp("dataModel", dataModelName)
        .build();
    return FlowModelMeta.builder()
        .name("dataModelFlow")
        .node(node)
        .entryNode("模型")
        .exitNode("模型")
        .build();
  }

  private static DataModel model(String name) {
    var id = new DataModelFieldMeta();
    id.setName("id");
    id.setType("String");

    var meta = new DataModelMeta();
    meta.setName(name);
    meta.setFields(Collections.singletonList(id));
    meta.setPrimaryKeys(Collections.singletonList("id"));
    return new MetadataOnlyDataModel(meta);
  }

  private static java.util.List<NodeDesignException> errors(DefaultFlowModel flowModel) {
    var errors = new ArrayList<NodeDesignException>();
    for (DesignException error : flowModel.getErrors()) {
      if (error instanceof NodeDesignException) {
        errors.add((NodeDesignException) error);
      }
      if (error instanceof ProblematicNodesDetectedException) {
        errors.addAll(((ProblematicNodesDetectedException) error).getNodeDesignExceptionList());
      }
    }
    return errors;
  }
}

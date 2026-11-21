package com.ouroboros.businessmodel.projection.logicflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.ouroboros.businessmodel.util.PrototypeTemplate;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.logicflow.model.FlowConnectionMeta;
import com.ouroboros.logicflow.model.FlowModelFactory;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.logicflow.nodes.DataModelReferences;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;
import com.ouroboros.logicflow.nodes.NodeReferenceSet;

class LogicFlowTemplateStructureContractTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void singleScriptPassesBothRequestAndDataModelWithProductionNodes(boolean connectedModelInput) {
    var metadata = PrototypeTemplate.generate("logic-flow/singleScript", Map.of(
        "fullName", "demo.customer.singleScript", "modelName", LogicFlowTemplateContractTestSupport.MODEL_FULL_NAME,
        "scriptLanguage", "groovy", "script", "return [data: $request.data, model: $dataModel.fullName]"), FlowModelMeta.class);
    if (connectedModelInput) {
      metadata = metadata.map(builder -> builder.connection(FlowConnectionMeta.builder()
          .sourceNode("接受输入").sourceOutputPort(0)
          .targetNode("获取数据模型").targetInputPort(0).build()));
    }
    var dataModel = mock(DataModel.class);
    when(dataModel.getFullName()).thenReturn(LogicFlowTemplateContractTestSupport.MODEL_FULL_NAME);
    var references = NodeReferenceCatalog.of(List.of(NodeReferenceSet.complete(
        DataModelReferences.KIND,
        Map.of(LogicFlowTemplateContractTestSupport.MODEL_FULL_NAME, dataModel),
        String::compareToIgnoreCase)));

    var flow = FlowModelFactory.build(metadata, references);

    assertTrue(flow.isValid(), () -> "singleScript must compile with production nodes: " + flow.getErrors());
    assertEquals(Map.of("data", Map.of("name", "customer"), "model", LogicFlowTemplateContractTestSupport.MODEL_FULL_NAME),
        flow.execute(Map.of("data", Map.of("name", "customer"))).get());
  }

  @Test
  void allRootTemplatesShouldUseExplicitSuccessfulTerminalContract() throws Exception {
    LogicFlowTemplateContractTestSupport.bindMinimalDataModelCenter();

    var templateCases = Arrays.asList(
        new TemplateCase("logic-flow/singleScript", true),
        new TemplateCase("logic-flow/add", true),
        new TemplateCase("logic-flow/delete", true),
        new TemplateCase("logic-flow/update", true),
        new TemplateCase("logic-flow/search", true),
        new TemplateCase("logic-flow/getOne", true, Collections.singletonList("收集数据")),
        new TemplateCase("logic-flow/import", true),
        new TemplateCase("logic-flow/batchDelete", true),
        new TemplateCase("logic-flow/workflow/startProcess", true),
        new TemplateCase("logic-flow/workflow/initTaskForm", true, Collections.singletonList("输出结果")),
        new TemplateCase("logic-flow/workflow/submitTask", true, Collections.singletonList("输出响应")),
        new TemplateCase("logic-flow/workflow/processCallback", true, Collections.singletonList("返回响应"))
    );

    assertEquals(12, templateCases.size(), "expected all 12 logic-flow root templates to be covered");

    for (var templateCase : templateCases) {
      var flowModel = LogicFlowTemplateContractTestSupport.generateTemplate(templateCase.templateName);
      LogicFlowTemplateContractTestSupport.assertExplicitSuccessfulTerminalContract(templateCase.templateName, flowModel);
      if (templateCase.shouldValidateFlow) {
        LogicFlowTemplateContractTestSupport.assertExplicitSuccessfulTerminalRemainsValid(templateCase.templateName, flowModel);
      }
      for (var absentNodeName : templateCase.absentNodeNames) {
        LogicFlowTemplateContractTestSupport.assertNodeAbsent(templateCase.templateName, flowModel, absentNodeName);
      }
    }
  }

  private static final class TemplateCase {
    private final String templateName;
    private final boolean shouldValidateFlow;
    private final List<String> absentNodeNames;

    private TemplateCase(String templateName, boolean shouldValidateFlow) {
      this(templateName, shouldValidateFlow, Collections.emptyList());
    }

    private TemplateCase(String templateName, boolean shouldValidateFlow, List<String> absentNodeNames) {
      this.templateName = templateName;
      this.shouldValidateFlow = shouldValidateFlow;
      this.absentNodeNames = absentNodeNames;
    }
  }
}

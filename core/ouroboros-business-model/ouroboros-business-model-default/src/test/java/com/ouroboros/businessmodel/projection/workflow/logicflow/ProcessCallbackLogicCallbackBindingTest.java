package com.ouroboros.businessmodel.projection.workflow.logicflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.BusinessModelProvider;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.workflow.approval.callback.CallbackBinding;

/**
 * M23-T2 回调绑定元数据投影测试：ProcessCallbackLogic 投影时产出 CallbackBinding
 * （flowKey/statusField/statusMap 与 workflow.bindings[0] 同源）。
 */
class ProcessCallbackLogicCallbackBindingTest {

  private static final BusinessModelProvider EMPTY_BUSINESS_MODEL_PROVIDER = new BusinessModelProvider() {
    @Override
    public Optional<BusinessModel> getBusinessModel(String fullName) {
      return Optional.empty();
    }

    @Override
    public Map<String, BusinessModel> getBusinessModelMap() {
      return Collections.emptyMap();
    }
  };

  @Test
  void projectionEmitsCallbackBindingWithStatusMetadata() {
    DefaultBusinessModelMeta meta = new DefaultBusinessModelMeta();
    meta.setName("demo.order");
    meta.setTitle("订单");
    Map<String, Object> workflow = new LinkedHashMap<>();
    Map<String, Object> binding = new LinkedHashMap<>();
    binding.put("processId", "process-demo");
    binding.put("statusField", "status");
    Map<String, Object> statusMap = new LinkedHashMap<>();
    statusMap.put("approved", Map.of("code", "APPROVED"));
    statusMap.put("processing", Map.of("code", "PROCESSING"));
    binding.put("statusMap", statusMap);
    workflow.put("bindings", java.util.List.of(binding));
    meta.setExtraProp("workflow", workflow);
    DefaultBusinessModel businessModel =
        new DefaultBusinessModel(meta, EMPTY_BUSINESS_MODEL_PROVIDER);

    ModelProjectionPipelineContext<DefaultBusinessModel> context =
        new ModelProjectionPipelineContext<>(businessModel);
    new ProcessCallbackLogic().doProcess(context);

    CallbackBinding bindingMeta = context.getModel(CallbackBinding.class, "demo.order")
        .orElseThrow(() -> new AssertionError("投影未产出 CallbackBinding"));
    assertThat(bindingMeta.flowKey()).isEqualTo("process-demo");
    assertThat(bindingMeta.callbackFlow()).isEqualTo("demo.order.processCallback");
    assertThat(bindingMeta.recordType()).isEqualTo("demo.order");
    assertThat(bindingMeta.statusField()).isEqualTo("status");
    assertThat(bindingMeta.statusMap()).containsEntry("approved", Map.of("code", "APPROVED"));
  }

  @Test
  void projectionWithoutProcessIdEmitsNoBinding() {
    DefaultBusinessModelMeta meta = new DefaultBusinessModelMeta();
    meta.setName("demo.order");
    Map<String, Object> workflow = new LinkedHashMap<>();
    workflow.put("bindings", java.util.List.of(Map.of("statusField", "status")));
    meta.setExtraProp("workflow", workflow);
    DefaultBusinessModel businessModel =
        new DefaultBusinessModel(meta, EMPTY_BUSINESS_MODEL_PROVIDER);

    ModelProjectionPipelineContext<DefaultBusinessModel> context =
        new ModelProjectionPipelineContext<>(businessModel);
    new ProcessCallbackLogic().doProcess(context);

    assertThat(context.getModel(CallbackBinding.class, "demo.order")).isEmpty();
  }
}

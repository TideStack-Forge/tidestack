package com.ouroboros.workflow.approval.web.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.vavr.control.Try;

import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.workflow.approval.FlowDefinitionService;
import com.ouroboros.workflow.approval.FlowInstanceService;
import com.ouroboros.workflow.approval.definition.FlowDefinition;
import com.ouroboros.workflow.approval.manage.service.WorkflowModelService;
import com.ouroboros.workflow.approval.model.FlowDurationStatistic;
import com.ouroboros.workflow.approval.model.FlowInstanceDetail;
import com.ouroboros.workflow.approval.model.FlowInstanceStatistic;
import com.ouroboros.workflow.approval.model.FlowTimeline;
import com.ouroboros.workflow.approval.web.controller.ApprovalWorkflowInstanceController;
import com.ouroboros.workflow.approval.web.user.CandidateUserResolverRegistry;
import com.ouroboros.workflow.approval.web.user.UsernameResolverRegistry;

class ApprovalWorkflowInstanceControllerIntegrationTest {
  private MockMvc mockMvc;
  private CapturingFlowInstanceService flowInstanceService;

  @BeforeEach
  void setUp() {
    flowInstanceService = new CapturingFlowInstanceService();
    FlowDefinitionService flowDefinitionService = unsupportedProxy(FlowDefinitionService.class);
    WorkflowModelService workflowModelService = unsupportedProxy(WorkflowModelService.class);
    CandidateUserResolverRegistry candidateUserResolverRegistry = new CandidateUserResolverRegistry(Collections.emptyList());
    UsernameResolverRegistry usernameResolverRegistry = new UsernameResolverRegistry(Collections.emptyList());

    ApprovalWorkflowInstanceController controller = new ApprovalWorkflowInstanceController();
    setField(controller, "flowInstanceService", flowInstanceService);
    setField(controller, "flowDefinitionService", flowDefinitionService);
    setField(controller, "workflowModelService", workflowModelService);
    setField(controller, "candidateUserResolverRegistry", candidateUserResolverRegistry);
    setField(controller, "usernameResolverRegistry", usernameResolverRegistry);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    CurrentSecurityContextHolder.setDelegate(() -> java.util.Optional.of(new DefaultSecurityIdentity(() -> "admin")));
  }

  @AfterEach
  void tearDown() {
    CurrentSecurityContextHolder.clearDelegate();
  }

  @Test
  void startSerialApprovalFlowMapsTaskAssigneesAndPreservesStepOrder() throws Exception {
    flowInstanceService.nextInstanceId = "instance-serial";

    String body = "{\"formData\":{\"amount\":1200},\"taskAssignee\":{\"multiapproval\":[{\"id\":\"user-a\",\"name\":\"A\"},{\"id\":\"user-b\",\"name\":\"B\"},{\"id\":\"user-c\",\"name\":\"C\"}]}}";

    mockMvc.perform(post("/api/approval-workflow/instance/flow-serial/start").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data").value("instance-serial"));

    assertEquals("flow-serial", flowInstanceService.flowKey);
    assertEquals("admin", flowInstanceService.userId);
    assertEquals(Arrays.asList("user-a", "user-b", "user-c"), flowInstanceService.taskAssignee.get("multiapproval"));
    assertEquals(Map.of(), flowInstanceService.instanceVariables);
    assertEquals(1200, flowInstanceService.formData.get("amount"));
  }

  @Test
  void startParallelApprovalFlowMapsAllSelectedApproversForParallelNode() throws Exception {
    flowInstanceService.nextInstanceId = "instance-parallel";

    String body = "{\"formData\":{\"amount\":3600},\"taskAssignee\":{\"multiapproval\":[{\"value\":\"user-a\",\"label\":\"A\"},{\"value\":\"user-b\",\"label\":\"B\"},{\"value\":\"user-c\",\"label\":\"C\"}]}}";

    mockMvc.perform(post("/api/approval-workflow/instance/flow-parallel/start").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data").value("instance-parallel"));

    assertEquals("flow-parallel", flowInstanceService.flowKey);
    assertEquals("admin", flowInstanceService.userId);
    assertEquals(Arrays.asList("user-a", "user-b", "user-c"), flowInstanceService.taskAssignee.get("multiapproval"));
  }

  @Test
  void startFlowMapsUserPickerIdentityShape() throws Exception {
    flowInstanceService.nextInstanceId = "instance-user-picker";

    String body = "{\"formData\":{\"amount\":2400},\"taskAssignee\":{\"approval\":[{\"userIdentity\":\"awf.approver1\",\"displayName\":\"Codex Approver\"}]}}";

    mockMvc.perform(post("/api/approval-workflow/instance/flow-user-picker/start").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data").value("instance-user-picker"));

    assertEquals("flow-user-picker", flowInstanceService.flowKey);
    assertEquals("admin", flowInstanceService.userId);
    assertEquals(Collections.singletonList("awf.approver1"), flowInstanceService.taskAssignee.get("approval"));
  }

  @Test
  void startMixedApprovalFlowMapsSerialAndParallelNodesWithinSingleRequest() throws Exception {
    flowInstanceService.nextInstanceId = "instance-mixed";

    String body = "{\"formData\":{\"category\":\"purchase\"},\"taskAssignee\":{\"multi-serial\":[{\"id\":\"user-a\",\"name\":\"A\"},{\"id\":\"user-b\",\"name\":\"B\"}],\"multi-parallel\":[{\"id\":\"user-c\",\"name\":\"C\"},{\"id\":\"user-d\",\"name\":\"D\"}]}}";

    mockMvc.perform(post("/api/approval-workflow/instance/flow-mixed/start").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data").value("instance-mixed"));

    assertEquals("flow-mixed", flowInstanceService.flowKey);
    assertEquals("admin", flowInstanceService.userId);
    Map<String, List<String>> assignees = flowInstanceService.taskAssignee;
    assertEquals(Arrays.asList("user-a", "user-b"), assignees.get("multi-serial"));
    assertEquals(Arrays.asList("user-c", "user-d"), assignees.get("multi-parallel"));
  }

  private <T> T unsupportedProxy(Class<T> type) {
    return type.cast(Proxy.newProxyInstance(
        type.getClassLoader(),
        new Class<?>[] { type },
        (proxy, method, args) -> {
          if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
          }
          throw new UnsupportedOperationException(method.getName() + " is not used by this test");
        }));
  }

  private void setField(Object target, String fieldName, Object value) {
    try {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to set field " + fieldName, e);
    }
  }

  private static class CapturingFlowInstanceService implements FlowInstanceService {
    private String nextInstanceId;
    private String flowKey;
    private String userId;
    private Map<String, List<String>> taskAssignee;
    private Map<String, Object> instanceVariables;
    private Map<String, Object> formData;

    @Override
    public Try<String> start(
        String flowKey,
        String userId,
        Map<String, List<String>> taskAssignee,
        Map<String, Object> instanceVariables,
        Map<String, Object> formData,
        CompletableFuture<Void> initTaskDeferred) {
      this.flowKey = flowKey;
      this.userId = userId;
      this.taskAssignee = taskAssignee;
      this.instanceVariables = instanceVariables;
      this.formData = formData;
      return Try.success(nextInstanceId);
    }

    @Override
    public Try<Void> pause(String instanceId, String userId, String comment) {
      throw new UnsupportedOperationException("pause is not used by this test");
    }

    @Override
    public Try<Void> resume(String instanceId, String userId) {
      throw new UnsupportedOperationException("resume is not used by this test");
    }

    @Override
    public Try<Void> cancel(String instanceId, String userId, String comment) {
      throw new UnsupportedOperationException("cancel is not used by this test");
    }

    @Override
    public Try<Void> complete(String instanceId, String userId, Boolean approved, String comment) {
      throw new UnsupportedOperationException("complete is not used by this test");
    }

    @Override
    public List<FlowTimeline> getTimeLine(String instanceId) {
      throw new UnsupportedOperationException("getTimeLine is not used by this test");
    }

    @Override
    public Try<SchemaNode> getInitialForm(String flowKey) {
      throw new UnsupportedOperationException("getInitialForm is not used by this test");
    }

    @Override
    public List<FlowInstanceStatistic> getStatistic(String keyword, String status, Integer offset, Integer limit) {
      throw new UnsupportedOperationException("getStatistic is not used by this test");
    }

    @Override
    public Long getStatisticCount(String keyword, String status) {
      throw new UnsupportedOperationException("getStatisticCount is not used by this test");
    }

    @Override
    public FlowDurationStatistic getDurationStatistic(String flowKey, java.util.Date from, java.util.Date to) {
      throw new UnsupportedOperationException("getDurationStatistic is not used by this test");
    }

    @Override
    public List<FlowInstanceDetail> getInstances(String flowKey, String status, Integer offset, Integer limit) {
      throw new UnsupportedOperationException("getInstances is not used by this test");
    }

    @Override
    public Long getInstancesCount(String flowKey, String status) {
      throw new UnsupportedOperationException("getInstancesCount is not used by this test");
    }

    @Override
    public FlowDefinition getInstanceFlowDefinition(String instanceId) {
      throw new UnsupportedOperationException("getInstanceFlowDefinition is not used by this test");
    }
  }
}

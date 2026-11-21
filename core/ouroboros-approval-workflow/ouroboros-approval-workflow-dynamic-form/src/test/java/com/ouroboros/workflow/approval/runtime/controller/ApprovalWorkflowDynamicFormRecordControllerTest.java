package com.ouroboros.workflow.approval.runtime.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.FlowInstanceService;
import com.ouroboros.workflow.approval.dynamicform.service.DynamicFormWorkflowRecordService;
import com.ouroboros.workflow.approval.dynamicform.model.DynamicFormWorkflowBinding;
import com.ouroboros.workflow.approval.dynamicform.model.DynamicFormWorkflowHistory;
import com.ouroboros.workflow.approval.model.FlowTimeline;

class ApprovalWorkflowDynamicFormRecordControllerTest {

  @Test
  void listBindingsRequiresCurrentUserToParticipateInInstanceAndPassesPagination() {
    var recordService = new RecordingRecordService();
    FlowInstanceService flowInstanceService = mock(FlowInstanceService.class);
    when(flowInstanceService.getTimeLine("instance-1")).thenReturn(List.of(timeline("approver-1")));
    var controller = new TestableController(recordService, flowInstanceService, "approver-1");

    var result = controller.listBindings("instance-1", "expense", "form-1", 2, 20);

    assertEquals(0, result.getCode());
    assertEquals(1L, result.getData().getTotal());
    assertEquals("instance-1", recordService.bindingInstanceId);
    assertEquals("expense", recordService.bindingFormKey);
    assertEquals("form-1", recordService.bindingFormId);
    assertEquals(20, recordService.bindingOffset);
    assertEquals(20, recordService.bindingLimit);
  }

  @Test
  void listHistoryRejectsUsersOutsideInstanceTimeline() {
    var recordService = new RecordingRecordService();
    FlowInstanceService flowInstanceService = mock(FlowInstanceService.class);
    when(flowInstanceService.getTimeLine("instance-1")).thenReturn(List.of(timeline("approver-1")));
    var controller = new TestableController(recordService, flowInstanceService, "other-user");

    var result = controller.listHistory("instance-1", null, "expense", "form-1", 1, 20);

    assertEquals(403, result.getCode());
    assertEquals(0, recordService.historyCalls);
  }

  @Test
  void listHistoryAllowsStarterRecordedOnTimeline() {
    var recordService = new RecordingRecordService();
    FlowInstanceService flowInstanceService = mock(FlowInstanceService.class);
    when(flowInstanceService.getTimeLine("instance-1")).thenReturn(List.of(timeline("starter-1")));
    var controller = new TestableController(recordService, flowInstanceService, "starter-1");

    var result = controller.listHistory("instance-1", null, "expense", "form-1", 1, 20);

    assertEquals(0, result.getCode());
    assertEquals(1, recordService.historyCalls);
  }

  @Test
  void listBindingsRejectsBlankInstanceBeforeQueryingTimeline() {
    var recordService = new RecordingRecordService();
    FlowInstanceService flowInstanceService = mock(FlowInstanceService.class);
    var controller = new TestableController(recordService, flowInstanceService, "approver-1");

    var result = controller.listBindings(" ", "expense", "form-1", 1, 20);

    assertEquals(400, result.getCode());
    verifyNoInteractions(flowInstanceService);
    assertEquals(0, recordService.bindingCalls);
  }

  private static FlowTimeline timeline(String userId) {
    var task = new FlowTimeline.TaskInstance();
    task.setCandidateUsers(Collections.singletonList(userId));
    var timeline = new FlowTimeline();
    timeline.setTaskInstances(Collections.singletonList(task));
    return timeline;
  }

  private static class TestableController extends ApprovalWorkflowDynamicFormRecordController {
    private final String currentUserId;

    private TestableController(DynamicFormWorkflowRecordService recordService,
                               FlowInstanceService flowInstanceService,
                               String currentUserId) {
      super(recordService, flowInstanceService);
      this.currentUserId = currentUserId;
    }

    @Override
    protected String currentUserId() {
      return currentUserId;
    }
  }

  private static class RecordingRecordService extends DynamicFormWorkflowRecordService {
    private String bindingInstanceId;
    private String bindingFormKey;
    private String bindingFormId;
    private Integer bindingOffset;
    private Integer bindingLimit;
    private int bindingCalls;
    private int historyCalls;

    @Override
    public Long countBindings(String instanceId, String formKey, String formId) {
      return 1L;
    }

    @Override
    public List<DynamicFormWorkflowBinding> listBindings(String instanceId, String formKey, String formId,
                                                         Integer offset, Integer limit) {
      bindingCalls++;
      bindingInstanceId = instanceId;
      bindingFormKey = formKey;
      bindingFormId = formId;
      bindingOffset = offset;
      bindingLimit = limit;
      var binding = new DynamicFormWorkflowBinding();
      binding.setInstanceId(instanceId);
      binding.setFormKey(formKey);
      binding.setFormId(formId);
      return Collections.singletonList(binding);
    }

    @Override
    public Long countHistory(String instanceId, String taskId, String formKey, String formId) {
      return 1L;
    }

    @Override
    public List<DynamicFormWorkflowHistory> listHistory(String instanceId, String taskId, String formKey,
                                                        String formId, Integer offset, Integer limit) {
      historyCalls++;
      return Collections.singletonList(new DynamicFormWorkflowHistory());
    }
  }
}

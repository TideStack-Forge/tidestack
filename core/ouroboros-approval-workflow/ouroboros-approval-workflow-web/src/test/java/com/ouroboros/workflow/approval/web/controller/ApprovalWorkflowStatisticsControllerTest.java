package com.ouroboros.workflow.approval.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;

import org.junit.jupiter.api.Test;

import com.ouroboros.web.WebApiResult;
import com.ouroboros.workflow.approval.FlowInstanceService;
import com.ouroboros.workflow.approval.model.FlowDurationStatistic;
import com.ouroboros.workflow.approval.web.user.UsernameResolverRegistry;

class ApprovalWorkflowStatisticsControllerTest {
  @Test
  void durationReturnsRoadmapShapeAndResolvesApproverNames() {
    FlowInstanceService flowInstanceService = mock(FlowInstanceService.class);
    UsernameResolverRegistry usernameResolverRegistry = mock(UsernameResolverRegistry.class);
    ApprovalWorkflowStatisticsController controller = new ApprovalWorkflowStatisticsController();
    controller.flowInstanceService = flowInstanceService;
    controller.usernameResolverRegistry = usernameResolverRegistry;

    FlowDurationStatistic statistic = buildDurationStatistic();
    when(flowInstanceService.getDurationStatistic(eq("flow-a"), any(Date.class), any(Date.class)))
        .thenReturn(statistic);
    when(usernameResolverRegistry.resolve(Collections.singletonList("approver-1")))
        .thenReturn(Collections.singletonMap("approver-1", "张三"));

    WebApiResult<FlowDurationStatistic> result = controller.duration(
        "flow-a",
        "2026-01-01T00:00:00Z",
        "2026-01-02T00:00:00Z");

    assertEquals(0, result.getCode());
    assertEquals("flow-a", result.getData().getFlowKey());
    assertEquals(1L, result.getData().getInstanceCount());
    assertEquals(7200000L, result.getData().getAvgDurationMillis());
    assertEquals("approve-a", result.getData().getNodes().get(0).getNodeId());
    assertEquals("经理审批", result.getData().getNodes().get(0).getNodeName());
    assertEquals(2L, result.getData().getNodes().get(0).getTaskCount());
    assertEquals(3600000L, result.getData().getNodes().get(0).getAvgDurationMillis());
    assertEquals("approver-1", result.getData().getApprovers().get(0).getUserId());
    assertEquals("张三", result.getData().getApprovers().get(0).getUserName());
    assertEquals(2L, result.getData().getApprovers().get(0).getHandledCount());
    assertEquals(1800000L, result.getData().getApprovers().get(0).getAvgHandleMillis());
    verify(flowInstanceService).getDurationStatistic(eq("flow-a"), any(Date.class), any(Date.class));
  }

  @Test
  void durationRejectsInvalidRangeBeforeCallingService() {
    FlowInstanceService flowInstanceService = mock(FlowInstanceService.class);
    UsernameResolverRegistry usernameResolverRegistry = mock(UsernameResolverRegistry.class);
    ApprovalWorkflowStatisticsController controller = new ApprovalWorkflowStatisticsController();
    controller.flowInstanceService = flowInstanceService;
    controller.usernameResolverRegistry = usernameResolverRegistry;

    WebApiResult<FlowDurationStatistic> result = controller.duration(
        "flow-a",
        "2026-01-02T00:00:00Z",
        "2026-01-01T00:00:00Z");

    assertEquals(-4000, result.getCode());
    assertEquals("invalid_range", result.getMsg());
    verify(flowInstanceService, never()).getDurationStatistic(eq("flow-a"), any(Date.class), any(Date.class));
  }

  private FlowDurationStatistic buildDurationStatistic() {
    FlowDurationStatistic statistic = new FlowDurationStatistic();
    statistic.setFlowKey("flow-a");
    statistic.setInstanceCount(1L);
    statistic.setAvgDurationMillis(7200000L);

    FlowDurationStatistic.NodeDurationStatistic node = new FlowDurationStatistic.NodeDurationStatistic();
    node.setNodeId("approve-a");
    node.setNodeName("经理审批");
    node.setTaskCount(2L);
    node.setAvgDurationMillis(3600000L);
    statistic.setNodes(Collections.singletonList(node));

    FlowDurationStatistic.ApproverDurationStatistic approver = new FlowDurationStatistic.ApproverDurationStatistic();
    approver.setUserId("approver-1");
    approver.setUserName("approver-1");
    approver.setHandledCount(2L);
    approver.setAvgHandleMillis(1800000L);
    statistic.setApprovers(Collections.singletonList(approver));
    return statistic;
  }
}

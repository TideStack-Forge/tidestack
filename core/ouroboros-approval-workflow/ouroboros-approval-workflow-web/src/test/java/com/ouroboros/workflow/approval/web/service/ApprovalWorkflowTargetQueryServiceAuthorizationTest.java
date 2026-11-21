package com.ouroboros.workflow.approval.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.workflow.approval.FlowTaskService;
import com.ouroboros.workflow.approval.model.FlowTaskDetail;
import com.ouroboros.workflow.approval.web.user.UsernameResolverRegistry;
import com.ouroboros.workflow.approval.web.vo.TargetItemVO;

/**
 * 动作目标查询的归属校验：只有任务的当前处理人或候选人可以查询目标列表。
 *
 * <p>此前六个查询只校验"任务存在"，任何持有审批中心权限的用户拿到 taskId 即可枚举他人任务的
 * 转办对象、加签对象、驳回/恢复目标节点与参与人名单（IDOR）。
 */
class ApprovalWorkflowTargetQueryServiceAuthorizationTest {

  private FlowTaskService flowTaskService;
  private ApprovalWorkflowTargetQueryService service;

  @BeforeEach
  void setUp() {
    flowTaskService = mock(FlowTaskService.class);
    service = new ApprovalWorkflowTargetQueryService();
    ReflectionTestUtils.setField(service, "flowTaskService", flowTaskService);
    ReflectionTestUtils.setField(service, "usernameResolverRegistry", mock(UsernameResolverRegistry.class));
  }

  @Test
  void rejectsUserWhoIsNeitherAssigneeNorCandidate() {
    when(flowTaskService.getTaskDetail("task-1"))
        .thenReturn(task("task-1", "user-1", Collections.singletonList("user-2")));

    assertThatThrownBy(() -> service.getTransferTargets("task-1", "intruder"))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.getRejectTargets("task-1", "intruder"))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.getRecoveryTargets("task-1", "intruder"))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.getFreeApprovalTargets("task-1", "intruder"))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void rejectsBlankUserId() {
    assertThatThrownBy(() -> service.getAddSignTargets("task-1", " "))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void allowsCurrentAssignee() {
    when(flowTaskService.getTaskDetail("task-1"))
        .thenReturn(task("task-1", "user-1", Collections.singletonList("user-2")));
    when(flowTaskService.getCandidateUsers("task-1")).thenReturn(Collections.emptyList());

    List<TargetItemVO> targets = service.getTransferTargets("task-1", "user-1");

    assertThat(targets).isEmpty();
  }

  @Test
  void allowsCandidate() {
    when(flowTaskService.getTaskDetail("task-1"))
        .thenReturn(task("task-1", "user-1", Collections.singletonList("user-2")));
    when(flowTaskService.getCandidateUsers("task-1")).thenReturn(Arrays.asList("user-2", "user-3"));

    List<TargetItemVO> targets = service.getAddSignTargets("task-1", "user-2");

    assertThat(targets).isNotEmpty();
  }

  private FlowTaskDetail task(String id, String assignee, List<String> candidateUsers) {
    FlowTaskDetail detail = new FlowTaskDetail();
    detail.setId(id);
    detail.setAssignee(assignee);
    detail.setCandidateUsers(candidateUsers);
    return detail;
  }
}

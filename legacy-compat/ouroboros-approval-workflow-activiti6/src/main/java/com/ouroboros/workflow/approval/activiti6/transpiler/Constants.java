package com.ouroboros.workflow.approval.activiti6.transpiler;

public class Constants {

  public static final String OUROBOROS_NAMESPACE_PREFIX = "ouroborosApproval";
  public static final String OUROBOROS_NAMESPACE = "ouroboros/workflow/approval/1.0";
  // 流程暂停
  public static final String PROCESS_PAUSE_USER_VARIABLE_NAME = "process_pause_user";
  public static final String PROCESS_PAUSE_TIME_VARIABLE_NAME = "process_pause_time";
  public static final String PROCESS_PAUSE_REASON_VARIABLE_NAME = "process_pause_reason";
  // 流程取消
  public static final String PROCESS_RESUME_USER_VARIABLE_NAME = "process_resume_user";
  public static final String PROCESS_RESUME_TIME_VARIABLE_NAME = "process_resume_time";
  // 流程取消
  public static final String PROCESS_CANCEL_USER_VARIABLE_NAME = "process_cancel_user";
  public static final String PROCESS_CANCEL_TIME_VARIABLE_NAME = "process_cancel_time";
  public static final String PROCESS_CANCEL_REASON_VARIABLE_NAME = "process_cancel_reason";
  public static final String TASK_PAYLOAD_KEY = "__task_payload";
  public static final String TASK_FORM_TYPE_KEY = "__task_form_type";
  public static final String TASK_FORM_ID_KEY = "__task_form_id";
  public static final String TASK_FORM_DATA_KEY = "__task_form_data";

  // 特殊节点相关
  public static class SpecialNode {
    /* 流程全局节点 */
    public static final String START_EVENT_ID = "__start";
    public static final String START_EVENT_NAME = "开始节点";
    public static final String END_EVENT_ID = "__end_event";
    public static final String END_EVENT_NAME = "结束节点";
    public static final String INITIALIZE_FLOW_ID = "__initial_service_task";
    public static final String INITIALIZE_FLOW_NAME = "初始化";
    public static final String INITIAL_FORK_GATEWAY_ID = "__initial_fork_gateway";
    public static final String INITIAL_FORK_GATEWAY_NAME = "流程初始化分支网关";
    public static final String INITIAL_JOIN_GATEWAY_ID = "__initial_join_gateway";
    public static final String INITIAL_JOIN_GATEWAY_NAME = "流程初始化汇聚网关";
    public static final String FAIL_NODE_ID = "__fail_service_task";
    public static final String FAIL_NODE_NAME = "失败结束节点";

    /* 审批相关节点 */
    public static final String RESET_MULTIPLE_APPROVAL_ID_SUFFIX = "_reset_multiple_approval_service_task";
    public static final String RESET_MULTIPLE_APPROVAL_NAME = "重置会签状态";
    public static final String DECISION_ID_SUFFIX = "_decision_gateway";
    public static final String DECISION_NAME_SUFFIX = "审批结果决策";
    public static final String APPROVED_ID_SUFFIX = "_approved_decision";
    public static final String APPROVED_NAME_SUFFIX = "审批通过";
    public static final String OUTCOME_ID_SUFFIX = "_set_outcome_service_task";

    /* 分流相关节点 */
    public static final String JOIN_ID_SUFFIX = "_join_gateway";
    public static final String JOIN_NAME_SUFFIX = "汇聚网关";
  }

  // 流程变量相关
  public static class Variable {
    public static final String PROCESS_STARTER = "__processStarter";
    public static final String PROCESS_FLOW_KEY = "__processFlowKey";
    public static final String USER_SELECTED_ASSIGNEES = "__userSelectedAssignees";
    public static final String PROCESS_APPROVED = "__processApproved";
    public static final String PROCESS_OUTCOME = "__processOutcome";
    public static final String PROCESS_LAST_FINISHED_TASK_ID = "__processLastFinishedTaskId";
    public static final String PARENT_FLOW_ID = "__parentFlowId";
    public static final String HAS_PARENT_FLOW = "__hasParentFlow";
    public static final String MULTIPLE_APPROVAL_ASSIGNEE_LIST = "AssigneeList";

    public static final String LOCAL_APPROVAL_ACTION = "__approval_action";
    public static final String TASK_KIND = "__task_kind";
    public static final String TASK_APPROVED = "__task_approved";
    public static final String APPROVAL_ACTION_SUFFIX = "_approval_action";
    public static final String APPROVED_SUFFIX = "_approved";
    public static final String NR_OF_APPROVED_SUFFIX = "_nrOfApproved";
    public static final String NR_OF_REJECTED_SUFFIX = "_nrOfRejected";
    public static final String MULTIPLE_APPROVAL_COMPLETED_SUFFIX = "_multiple_approval_completed";
    public static final String MULTIPLE_APPROVED_PERCENTAGE_SUFFIX = "_approved_percentage";
    public static final String MULTIPLE_APPROVAL_IS_SEQUENTIAL_SUFFIX = "_is_sequential";
    public static final String MULTIPLE_APPROVAL_FINAL_TASK_ID_SUFFIX = "_multiple_approval_final_task_id";
    public static final String MULTIPLE_APPROVAL_SESSION = "__multiple_approval_session";

    public static String getMultipleApprovalAssigneeListVariableName(String approvalTaskId) {
      return approvalTaskId + "_" + MULTIPLE_APPROVAL_ASSIGNEE_LIST;
    }

    public static String getMultipleApprovalSessionVariableName(String approvalTaskId) {
      return approvalTaskId + MULTIPLE_APPROVAL_SESSION;
    }
  }

  public static class Property {
    public static final String PERCENTAGE = "percentage";
    public static final String MULTIPLE_APPROVAL_TASK_ID = "multipleApprovalTaskId";
    public static final String IS_SEQUENTIAL = "isSequential";
    public static final String PROCESS_OUTCOME = "processOutcome";
  }

  public static class Value {
    public static final String APPROVED = "approve";
    public static final String REJECTED = "reject";
    public static final String ROLLBACK = "rollback";
    public static final String COMPLETE = "complete";
    public static final String TASK_KIND_APPROVAL = "approval";
    public static final String TASK_KIND_HANDLE = "handle";
    public static final String EXCLUSIVE_FORK_TYPE = "exclusive";
    public static final String MULTIPLE_APPROVAL_MODE_SEQUENTIAL = "串行";
  }

}

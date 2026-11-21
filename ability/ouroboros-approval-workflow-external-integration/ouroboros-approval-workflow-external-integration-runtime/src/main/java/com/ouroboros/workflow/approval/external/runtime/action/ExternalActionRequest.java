package com.ouroboros.workflow.approval.external.runtime.action;

import java.util.Map;

import com.ouroboros.workflow.approval.external.ExternalInstanceRef;
import com.ouroboros.workflow.approval.external.ExternalTaskRef;

/** 进入可靠动作执行器前的业务请求；远程幂等键由持久化事实 ID 统一生成。 */
public record ExternalActionRequest(
    String canonicalAction,
    String externalAction,
    String userId,
    String comment,
    Map<String, Object> formData,
    Map<String, Object> payload,
    Integer mappingVersion,
    ExternalInstanceRef externalInstanceRef,
    ExternalTaskRef externalTaskRef) {
}
